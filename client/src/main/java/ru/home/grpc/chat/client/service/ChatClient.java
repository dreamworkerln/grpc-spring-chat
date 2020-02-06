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

    public static final int DEADLINE_DURATION = 3000;

    public static final String DEFAULT_PORT = "8090";

    private static final String CLIENT_ID = "client_id";

    private final static Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());


    private ClientEvents clientEvents;

    private ManagedChannel channel;
    private ChatServiceGrpc.ChatServiceBlockingStub blockingStub;
    private ChatServiceGrpc.ChatServiceStub asyncStub;
    private StreamObserver<ClientMessage> chat;
    private String login;
    private String password;

    @Autowired
    public void setApplicationEventPublisher(ClientEvents clientEvents) {
        this.clientEvents = clientEvents;
    }


    public void connect(String host, int port, String login, String password) throws AccessDeniedException {
        this.login = login;
        this.password = password;

        connect(ManagedChannelBuilder.forAddress(host, port).usePlaintext());
    }


    // Construct client for accessing Chat server using the existing channel.
    protected void connect(ManagedChannelBuilder<?> channelBuilder) throws AccessDeniedException {
        channel = channelBuilder
            .keepAliveTime(10, TimeUnit.SECONDS)
            .keepAliveTimeout(20, TimeUnit.SECONDS)
            .keepAliveWithoutCalls(true) // provide surviving NAT translation timeout dropping
            .build();

        start();
    }


    public void start() throws AccessDeniedException {

        blockingStub = ChatServiceGrpc.newBlockingStub(channel);
        asyncStub = ChatServiceGrpc.newStub(channel);

        log.info("Authenticating to server");

        // getting client id (access token, etc)
        // by authenticate yourself to server using login and password ------------------------------------------
        AuthRequest authRequest = AuthRequest.newBuilder()
            .setLogin(login)
            .setPassword(password)
            .build();

        AuthResponse response;

        response = blockingStub.withDeadlineAfter(DEADLINE_DURATION, TimeUnit.MILLISECONDS).authenticate(authRequest);

        int result = response.getResult();
        if(result != 200) {
            shutdown();
            log.info("Server banned us: " + result);
            throw new AccessDeniedException("Server response code error: " + result);
        }

        log.info("Connected to server.");

        // Assign token to asyncStub -----------------------------------------------------------------------------

        // assigning clientId to asyncStub as header value
        String id = response.getId();
        Assert.isTrue(!StringUtils.isBlank(id), "id is null");
        Metadata.Key<String> clientIdKey = Metadata.Key.of(CLIENT_ID, ASCII_STRING_MARSHALLER);
        Metadata fixedHeaders = new Metadata();
        fixedHeaders.put(clientIdKey, id);
        asyncStub = MetadataUtils.attachHeaders(asyncStub, fixedHeaders);

        // Dunno howto use deadline on asyncStub
        //chat = asyncStub.withDeadlineAfter(10000, TimeUnit.MILLISECONDS)
        //    .chat(new StreamObserver<ServerMessage>() {

        // Prepare to async chatting -----------------------------------------------------------------------------

        chat = asyncStub.chat(new StreamObserver<ServerMessage>() {

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
    }


    public void sendMessage(String message) {

        if (StringUtils.isBlank(message)) {
            return;
        }
        chat.onNext(ClientMessage.newBuilder()
            .setMessage(message)
            .build());
    }




    public void shutdown() {
        try {
            channel.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException ignore) {}
    }


    public boolean isConnected() {

        if (channel != null) {
            log.info(channel.getState(false).name());
        }

        return channel != null &&
               channel.getState(false) == ConnectivityState.READY;
    }

}
