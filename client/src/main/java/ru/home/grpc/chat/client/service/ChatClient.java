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
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;
import ru.home.grpc.chat.*;
import ru.home.grpc.chat.client.commands.ClientEvents;

import java.lang.invoke.MethodHandles;
import java.nio.file.AccessDeniedException;
import java.util.concurrent.TimeUnit;

import static io.grpc.Metadata.ASCII_STRING_MARSHALLER;


/**
 * Sample client code that makes gRPC calls to the server.
 */
@Service
public class ChatClient {

    private static final String CLIENT_BASIC = "basic_auth";
    private static final String CLIENT_TOKEN = "token_auth";

    private static final int DEADLINE_DURATION = 3;
    private static final int KEEP_ALIVE_TIME = 10;
    private static final int KEEP_ALIVE_TIMEOUT = 20;

    public static final String DEFAULT_PORT = "8090";

    private final static Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());


    private ClientEvents clientEvents;

    private ManagedChannel channel;
    private ConnectivityState lastState;
    private ChatServiceGrpc.ChatServiceBlockingStub authStub;
    private ChatServiceGrpc.ChatServiceStub chatStub;
    private StreamObserver<ClientMessage> chatObserver;

    private String login;
    private String password;

    private boolean authenticated;
    private boolean connected;

    @Autowired
    public void setApplicationEventPublisher(ClientEvents clientEvents) {
        this.clientEvents = clientEvents;
    }


    public void connect(String host, int port, String login, String password) {
        this.login = login;
        this.password = password;

        connect(ManagedChannelBuilder.forAddress(host, port).usePlaintext());
    }

    // Construct client for accessing Chat server using the existing channel.
    protected void connect(ManagedChannelBuilder<?> channelBuilder) {
        channel = channelBuilder
            .keepAliveTime(KEEP_ALIVE_TIME, TimeUnit.SECONDS)
            .keepAliveTimeout(KEEP_ALIVE_TIMEOUT, TimeUnit.SECONDS)
            .keepAliveWithoutCalls(true)
            .build();

        // setup callbacks on channel state changes
        setupStateHandlers();

        start();
    }


    public void start() {

        connected = false;
        authenticated = false;

        // Assign basicAuth to authStub -----------------------------------------------------------------------------

        authStub = ChatServiceGrpc.newBlockingStub(channel);

        // adding basicAuth header
        authStub = addBlockingStubHeader(authStub, login + ":" + password);

        log.debug("Authenticating to server");

        // getting client id (access token, etc)
        // by authenticate yourself to server using login and password ------------------------------------------
        AuthRequest authRequest = AuthRequest.newBuilder().build();

        AuthResponse response = null;

        response = authStub.withDeadlineAfter(DEADLINE_DURATION, TimeUnit.SECONDS).authenticate(authRequest);

        log.debug("Connected to server.");

        // Assign token to chatStub -----------------------------------------------------------------------------

        // add token to chatStub header
        String token = response.getToken();
        Assert.isTrue(!StringUtils.isBlank(token), "token is null");

        chatStub = ChatServiceGrpc.newStub(channel);
        chatStub = addAsyncStubHeader(chatStub, token);

        // Dunno howto use deadline on chatStub
        // chatObserver = chatStub.withDeadlineAfter(10000, TimeUnit.MILLISECONDS)
        //     .chatObserver(new StreamObserver<ServerMessage>() {
        // Prepare to async chatting -----------------------------------------------------------------------------


        chatObserver = chatStub.chat(new StreamObserver<ServerMessage>() {

            @Override
            public void onNext(ServerMessage reply) {
                clientEvents.onMessage(reply);
            }

            @Override
            public void onError(Throwable t) {
                clientEvents.onError(t);
            }

            @Override
            public void onCompleted() {
                clientEvents.onClose();
            }
        });

        // should set in last line or scheduled keepAlive(checking authenticated) may fail
        authenticated = true;
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

    public void shutdown() {
        try {
            channel.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException ignore) {}
    }


    public boolean isOnline() {
        return connected && authenticated;
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

    private void onStateChanged() {

        ConnectivityState currentState = channel.getState(false);

        log.debug("CHANNEL STATE CHANGED: {} -> {}", lastState, currentState);

        connected = currentState == ConnectivityState.READY;

        // re-apply due to channel.notifyWhenStateChanged -> Registers a one-off callback
        setupStateHandlers();
    }



    private void setupStateHandlers() {
        ConnectivityState state = channel.getState(false);
        lastState = state;
        channel.notifyWhenStateChanged(state, this::onStateChanged);
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

