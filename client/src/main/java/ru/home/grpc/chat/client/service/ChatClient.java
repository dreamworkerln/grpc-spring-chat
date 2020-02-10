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
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;
import ru.home.grpc.chat.*;
import ru.home.grpc.chat.client.shell.commands.ClientEvents;

import java.lang.invoke.MethodHandles;
import java.util.concurrent.TimeUnit;

import static io.grpc.Metadata.ASCII_STRING_MARSHALLER;


/**
 * Sample client code that makes gRPC calls to the server.
 */
@Service
public class ChatClient {

    private static final String CLIENT_BASIC = "basic_auth";
    private static final String CLIENT_TOKEN = "token_auth";

    public static final int DEADLINE_DURATION = 5;
    public static final int KEEP_ALIVE_TIME = 10;
    public static final int KEEP_ALIVE_TIMEOUT = 20;

    public static final String DEFAULT_PORT = "8090";

    private final static Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());


    private ClientEvents clientEvents;

    private ManagedChannel channel;
    private ConnectivityState currentState;
    private ConnectivityState previousState;
    private ChatServiceGrpc.ChatServiceBlockingStub blockingStub;
    private ChatServiceGrpc.ChatServiceStub asyncStub;
    private StreamObserver<ClientMessage> chatObserver;

    private String login;
    private String password;
    private String host;
    private int    port;

    private boolean authenticated;
    private boolean ready;
    private boolean shouldBeOnline;

    @Autowired
    public void setApplicationEventPublisher(ClientEvents clientEvents) {
        this.clientEvents = clientEvents;
    }


    public void init(String host, int port, String login, String password) {

        this.login = login;
        this.password = password;
        this.host = host;
        this.port = port;

        buildChannel();
    }


    public void connect() {

        Assert.notNull(channel, "Call init(...) first");

        // re-create channel
        if(channel.isShutdown() || channel.isTerminated()) {
            buildChannel();
        }

        authenticated = false;

        //channel.resetConnectBackoff();

        // setup callbacks on channel currentState changes
        setupStateHandlers();


//        Close previous observer (if exists) is that required?
//        if (chatObserver != null) {
//            try {
//                chatObserver.onCompleted();
//            }
//            catch (Exception ignore){}
//        }

        blockingStub = ChatServiceGrpc.newBlockingStub(channel);
        asyncStub = ChatServiceGrpc.newStub(channel);

        // adding basicAuth header
        blockingStub = addBlockingStubHeader(blockingStub, login + ":" + password);

        // getting client id (access token, etc)
        // by authenticate yourself to server using login and password ------------------------------------------

        log.debug("Authenticating to server");

        AuthRequest authRequest = AuthRequest.newBuilder().build();

        AuthResponse response = blockingStub.withDeadlineAfter(DEADLINE_DURATION, TimeUnit.SECONDS).authenticate(authRequest);

        log.debug("Connected to server");

        // Assign token to asyncStub -----------------------------------------------------------------------------

        // add token to asyncStub header
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
            public void onCompleted() {}
        });

        // only after all commands ow will get racing with onStateChanged()
        authenticated = true;
        shouldBeOnline = true;
        ready = true;
        // glitch fix, websarvar sometimes need moar time to change its state.
        // and you complete authentication her with state = IDLE
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

//
//        for (StackTraceElement ste : Thread.currentThread().getStackTrace()) {
//            System.out.println(ste);
//        }

        shouldBeOnline = false;
        if(channel!=null) {
            channel.shutdownNow();
        }
    }


    public boolean isOnline() {

        //System.out.println("ready:" + ready);
        //System.out.println("authenticated: " + authenticated);

        return ready && authenticated;
    }

    // -----------------------------------------------------------------------------

    private void onStateChanged() {

        currentState = channel.getState(false);

        log.debug("CHANNEL STATE CHANGED: {} -> {}", previousState, currentState);

        ready = currentState == ConnectivityState.READY;
        if (currentState != ConnectivityState.READY) {authenticated = false;}

        // check needs to reconnect
        handleState();

        // handle state changing
        clientEvents.onStateChanged(previousState, currentState);

        // re-apply notifyWhenStateChanged due to it registers a one-off callback
        setupStateHandlers();
    }



    private void handleState() {

        if (currentState == ConnectivityState.IDLE) {
            log.debug("Trying to reconnect - ping server");

            // will start gRPC built-in reconnection mechanism (use any rpc call to initiate it)
            try {
                Ping ping = Ping.newBuilder().setAck(false).build();
                blockingStub.withDeadlineAfter(DEADLINE_DURATION, TimeUnit.SECONDS).ping(ping);
            }
            catch (StatusRuntimeException e){
                log.debug("Server not responding");
            }
        }
        else if (currentState == ConnectivityState.READY) {

            // finally, channel connected again - need to re-authenticate or server will kick us
            if (shouldBeOnline && !authenticated) {
                log.debug("Reconnected, re-authenticating");

                try {
                    connect();
                }
                catch (StatusRuntimeException e){
                    log.debug("Authentication failed");
                }

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

    private void setupStateHandlers() {

        // stub to avoid NullPointerException
        if (currentState == null) {
            currentState = channel.getState(false);
        }
        previousState = currentState;

        channel.notifyWhenStateChanged(currentState, this::onStateChanged);
    }


    private void buildChannel() {

        shouldBeOnline = false;
        //shutdownNow();
        
        // build channel
        channel = ManagedChannelBuilder.forAddress(host, port).usePlaintext()
            .keepAliveTime(KEEP_ALIVE_TIME, TimeUnit.SECONDS)
            .keepAliveTimeout(KEEP_ALIVE_TIMEOUT, TimeUnit.SECONDS)
            .keepAliveWithoutCalls(true)
            .build();
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