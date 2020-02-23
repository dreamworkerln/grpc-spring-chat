/*
 * Copyright 2015 The gRPC Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package ru.home.grpc.chat.client.service;

import io.grpc.*;

import io.grpc.stub.MetadataUtils;
import io.grpc.stub.StreamObserver;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;
import ru.home.grpc.chat.*;
import ru.home.grpc.chat.client.shell.commands.ClientEvents;

import java.lang.invoke.MethodHandles;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static io.grpc.Metadata.ASCII_STRING_MARSHALLER;


/**
 * Sample client code that makes gRPC calls to the server.
 */
@Service
public class ChatClient {

    private static final String CLIENT_BASIC = "basic_auth";
    private static final String CLIENT_TOKEN = "token_auth";

    public static final int DEADLINE_DURATION = 5*100;
    public static final int KEEP_ALIVE_TIME = 10;
    public static final int KEEP_ALIVE_TIMEOUT = 20;

    public static final String DEFAULT_PORT = "8090";

    private final static Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());


    private ClientEvents clientEvents;

    private ManagedChannel channel;
    private ConnectivityState currentState = ConnectivityState.SHUTDOWN;
    private ConnectivityState previousState = ConnectivityState.SHUTDOWN;

    // without authentication headers
    private ChatServiceGrpc.ChatServiceBlockingStub pingStub;
    // with basic auth
    private ChatServiceGrpc.ChatServiceBlockingStub blockingStub;
    // with token auth
    private ChatServiceGrpc.ChatServiceStub asyncStub;

    private StreamObserver<ClientMessage> chatObserver;

    private String login;
    private String password;
    private String host;
    private int    port;

    // client is connected to server (currentState == ConnectivityState.READY)
    private boolean ready;
    // client is authenticated to server
    private boolean authenticated;
    // client should reconnect if connection failed
    private boolean shouldBeOnline;
    // if reconnecting now
    private AtomicBoolean reconnectPending = new AtomicBoolean();

    @Autowired
    public void setApplicationEventPublisher(ClientEvents clientEvents) {
        this.clientEvents = clientEvents;
    }


    public void init(String host, int port, String login, String password) {

        this.login = login;
        this.password = password;
        this.host = host;
        this.port = port;
    }


    /**
     * Connect to grpc server and authenticate yourself
     */
    public void connect() {

        //Assert.notNull(channel, "Call init(...) first");

        try {

            // get channel ready
            buildChannel();

            // init flags
            authenticated = false;
            shouldBeOnline = false;


            //channel.resetConnectBackoff();


            // will no auth header assigned
            pingStub = ChatServiceGrpc.newBlockingStub(channel);

            blockingStub = ChatServiceGrpc.newBlockingStub(channel);
            asyncStub = ChatServiceGrpc.newStub(channel);

            // adding basicAuth header
            blockingStub = addBlockingStubHeader(blockingStub, login + ":" + password);

            // Ping server before authenticating -----------------------------------------------------------------

            // Will not repeat because if server is unavailable
            // ping throw exception and client is shutdownNow()
            if(!ready) {
                log.debug("Ping server");
                ping();
            }

            // Authenticate yourself to server using login and password ------------------------------------------


            log.debug("Authenticating to server");

            AuthRequest authRequest = AuthRequest.newBuilder().build();

            // getting client id (access token, moar handy stuff)
            AuthResponse response = blockingStub.withDeadlineAfter(DEADLINE_DURATION, TimeUnit.SECONDS).authenticate(authRequest);

            log.debug("Connected to server");

            // Assign token to asyncStub header -------------------------------------------------------------------

            String token = response.getToken();
            Assert.isTrue(!StringUtils.isBlank(token), "token is null");

            asyncStub = addAsyncStubHeader(asyncStub, token);

            // Dunno howto use deadline on asyncStub
            // chatObserver = asyncStub.withDeadlineAfter(10000, TimeUnit.MILLISECONDS)
            //     .chatObserver(new StreamObserver<ServerMessage>() {
            // Prepare to async chatting -----------------------------------------------------------------------------


            chatObserver = asyncStub.chat(new StreamObserver<ServerMessage>() {

                @Override
                public void onNext(ServerMessage reply) {
                    clientEvents.onMessage(reply);
                }

                @Override
                public void onError(Throwable t) {
                    log.debug("gRPC error", t);
                    clientEvents.onError(t);
                }

                @Override
                public void onCompleted() {
                }
            });

            // set on only after all commands performed
            // or will get racing with onStateChanged()
            authenticated = true;
            shouldBeOnline = true;

            // Glitch fix, channel sometimes need moar time to change its state.
            // and you may complete authentication (perform rpc) here with connection.state = IDLE
            // So if you then call isOnline() you will get false.
            // Fixing this by setting manually
            ready = true;
        }
        catch (Exception e) {
            // something bad happened, closing client
            shutdownNow();
            throw e;
        }
    }


    // -----------------------------------------------------------------------------



    public void sendMessage(String message) {

        if (StringUtils.isBlank(message)) {
            return;
        }

        Assert.notNull(chatObserver, "chatObserver == null");

        chatObserver.onNext(ClientMessage.newBuilder()
            .setMessage(message)
            .build());
    }


    public void shutdownNow() {

        log.debug("Shutdown called");

//        for (StackTraceElement ste : Thread.currentThread().getStackTrace()) {
//            System.out.println(ste);
//        }

        ready = false;
        authenticated = false;
        shouldBeOnline = false;

        if(channel==null) {
            return;
        }

        if(!channel.isTerminated()) {
            channel.shutdownNow();
        }
    }


    /**
     * return is client connected and authenticated to server
     */
    public boolean isOnline() {

        //System.out.println("ready:" + ready);
        //System.out.println("authenticated: " + authenticated);

        return ready && authenticated;
    }

    // ==========================================================================



    /**
     * Activate onStateChanged() connection state change handler
     */
    private void setupStateHandlers() {


        previousState = currentState;

        // Имеет тенденцию тормозить, соответственно нельзя полагаться
        // что onStateChanged сработает сразу, как изменится состояние подключения

        // Одинрочный взвод, после срабатывания отваливается и требует повторного заряжания
        // gRPC dev-team think that is ok
        channel.notifyWhenStateChanged(currentState, this::onStateChanged);
    }


    /**
     * Handle connection state changes<br>
     *
     */
    private void onStateChanged() {

        currentState = channel.getState(false);

        log.debug("CHANNEL STATE CHANGED: {} -> {}", previousState, currentState);

        ready = currentState == ConnectivityState.READY;

        // drop authenticated flag if needed
        if (currentState != ConnectivityState.READY) {
            authenticated = false;
        }

        // check needs to reconnect
        checkNeedToReconnect();

        // handle state changing on client
        clientEvents.onStateChanged(previousState, currentState);

        // re-apply notifyWhenStateChanged due to it registers a one-off callback
        setupStateHandlers();
    }


    /**
     * Perform checkNeedToReconnect to server if needed
     */
    private void checkNeedToReconnect() {

        if (!shouldBeOnline) {
            return;
        }

        // if client is down - wake it up and try to connect to server
        // (using grpc built-in auto-reconnect mechanism)
        if (currentState == ConnectivityState.IDLE ) {

            log.debug("Trying to reconnect - pinging server");

            try {
                // Will repeat in background ping request till server up and response
                // (build-in grpc retry/reconnect mechanism)
                ping();
            }
            catch (StatusRuntimeException e){
                log.debug("Server not responding");
            }
        }

        // Well, finally we connected to the server, so we need to authenticate yourself,
        // otherwise we will be kicked out by server
        else if (currentState == ConnectivityState.READY && !authenticated) {

            reconnectPending.set(false);
            log.debug("Server up, re-authenticating");

            try {
                connect();
            }
            catch (StatusRuntimeException e){
                log.debug("Authentication failed");
            }
        }
    }


    private void ping() {

        // this ping request will start built-in grpc
        // retry/reconnect mechanism (perform any rpc call to start/initiate it)
        // it will try to perform rpc with increasing time interval on unsuccessful attempt
        try {
            Ping ping = Ping.newBuilder().setAck(false).build();
            //noinspection ResultOfMethodCallIgnored
            pingStub.withDeadlineAfter(DEADLINE_DURATION, TimeUnit.SECONDS).ping(ping);
        }
        catch (StatusRuntimeException e) {
            //log.debug("grpc error: ", e);

            // re-raise that server is unavailable
            if (e.getStatus().getCode() == Status.UNAVAILABLE.getCode()) {
                throw e;
            }
        }
    }


    // =============================================================================

    private ChatServiceGrpc.ChatServiceBlockingStub addBlockingStubHeader
        (ChatServiceGrpc.ChatServiceBlockingStub stub, String value) {

        Metadata.Key<String> clientIdKey = Metadata.Key.of(CLIENT_BASIC, ASCII_STRING_MARSHALLER);
        Metadata headers = new Metadata();
        headers.put(clientIdKey, value);

        return MetadataUtils.attachHeaders(stub, headers);

    }

    private ChatServiceGrpc.ChatServiceStub addAsyncStubHeader
        (ChatServiceGrpc.ChatServiceStub stub, String value) {

        Metadata.Key<String> clientIdKey = Metadata.Key.of(CLIENT_TOKEN, ASCII_STRING_MARSHALLER);
        Metadata headers = new Metadata();
        headers.put(clientIdKey, value);
        return MetadataUtils.attachHeaders(stub, headers);
    }


    // Rebuild if needed
    private void buildChannel() {

        // re-create channel
        if (channel == null ||
            channel.isShutdown() || channel.isTerminated()) {

            channel = ManagedChannelBuilder.forAddress(host, port).usePlaintext()
                .keepAliveTime(KEEP_ALIVE_TIME, TimeUnit.SECONDS)
                .keepAliveTimeout(KEEP_ALIVE_TIMEOUT, TimeUnit.SECONDS)
                .keepAliveWithoutCalls(true)
                .build();

            // setup callbacks on channel currentState changes
            setupStateHandlers();
        }
    }






}




//        if (authResponseCode != 200) {
//            shutdown();
//            log.info("Server banned us: " + authResponseCode);
//            throw new UnauthenticatedException("Server response code error: " + authResponseCode);
//









//    private Metadata addHeader(Metadata headers, String value) {
//        Metadata.Key<String> clientIdKey = Metadata.Key.of(CLIENT_ID, ASCII_STRING_MARSHALLER);
//        headers.put(clientIdKey, value);
//        return headers;
//    }







//            schedulerReconnect =
//                taskScheduler.scheduleWithFixedDelay(() -> {
//                        // reconnecting
//                        try {
//                            TimeUnit.SECONDS.sleep(KEEP_ALIVE_TIME / 2);
//                            connect();
//                        } catch (StatusRuntimeException ignored) {
//                            log.debug("Reconnect failed");
//                        } catch (InterruptedException e) {
//                            log.debug("Reconnect interrupted");
//                            Thread.currentThread().interrupt();
//                        }
//                    },
//                    Instant.now().plus(Duration.ofSeconds(KEEP_ALIVE_TIME / 2)), // задержка
//                    Duration.ofSeconds(KEEP_ALIVE_TIME / 2));                    // интервал




        /*


        if (currentState == ConnectivityState.IDLE) {

            log.debug("RECONNECING: SHUTDOWN ========================================================================== ");
            if(channel!=null) {
                channel.shutdownNow();
            }
        }
        else if (currentState == ConnectivityState.SHUTDOWN) {

            log.debug("RECONNECING: CONNECTING ======================================================================== ");

            schedulerReconnect =
                taskScheduler.scheduleWithFixedDelay(() -> {
                        // reconnecting
                        try {

                            channel.shutdownNow();
                            TimeUnit.SECONDS.sleep(KEEP_ALIVE_TIME / 2);
                            connect();
                        } catch (StatusRuntimeException ignored) {
                            log.debug("Reconnect failed");
                        } catch (InterruptedException e) {
                            log.debug("Reconnect interrupted");
                            Thread.currentThread().interrupt();
                        }
                    },
                    Instant.now().plus(Duration.ofSeconds(KEEP_ALIVE_TIME / 2)), // задержка
                    Duration.ofSeconds(KEEP_ALIVE_TIME / 2));                    // интервал
        }
        else if (currentState == ConnectivityState.READY) {

            log.debug("RECONNECING: CONNECTED ========================================================================== ");

            if(schedulerReconnect!=null) {
                schedulerReconnect.cancel(true);
            }
        }*/