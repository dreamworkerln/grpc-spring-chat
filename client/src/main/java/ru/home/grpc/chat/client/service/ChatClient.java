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
import org.springframework.util.Assert;
import ru.home.grpc.chat.*;

import java.lang.invoke.MethodHandles;
import java.util.concurrent.TimeUnit;

import static io.grpc.Metadata.ASCII_STRING_MARSHALLER;


/**
 * Sample client code that makes gRPC calls to the server.
 */
public class ChatClient {

    private static final String CLIENT_ID = "client_id";

    private final static Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    private final ManagedChannel channel;
    private ChatServiceGrpc.ChatServiceBlockingStub blockingStub;
    private ChatServiceGrpc.ChatServiceStub asyncStub;
    private StreamObserver<ClientMessage> chat;
    private String login;
    private String password;


    /** Construct client for accessing Chat server at {@code host:port}. */
    public ChatClient(String host, int port, String login, String password) {
        this(ManagedChannelBuilder.forAddress(host, port).usePlaintext());
        this.login = login;
        this.password = password;
    }

    /** Construct client for accessing Chat server using the existing channel. */
    public ChatClient(ManagedChannelBuilder<?> channelBuilder) {

        channel = channelBuilder
            .keepAliveTime(10, TimeUnit.SECONDS)
            .keepAliveTimeout(30, TimeUnit.SECONDS)
            .keepAliveWithoutCalls(true) // provide surviving NAT translation timeout dropping
            .build();
    }






    public void start() {

        blockingStub = ChatServiceGrpc.newBlockingStub(channel);
        asyncStub = ChatServiceGrpc.newStub(channel);


        // getting client id (access token, etc) by "authenticate" yourself to server using login
        AuthRequest authRequest = AuthRequest.newBuilder()
            .setLogin(login)
            .setPassword(password)
            .build();

        AuthResponse response = null;
        try {
            response = blockingStub.authenticate(authRequest);
        }
        catch (Exception e) {
            log.error("gRPC error: ", e);
            System.exit(1);

        }
        log.info("Connected to server.");


        int result = response.getResult();
        if(result != 200) {
            log.error("Server response code error: {}", result);
            System.exit(1);
        }

        // assigning clientId to asyncStub as header value
        String id = response.getId();

        Assert.isTrue(!StringUtils.isBlank(id), "id is null");

        Metadata.Key<String> clientIdKey = Metadata.Key.of(CLIENT_ID, ASCII_STRING_MARSHALLER);
        Metadata fixedHeaders = new Metadata();
        fixedHeaders.put(clientIdKey, id);

        asyncStub = MetadataUtils.attachHeaders(asyncStub, fixedHeaders);

        chat = asyncStub.chat(new StreamObserver<ServerMessage>() {
            @Override
            public void onNext(ServerMessage reply) {
                System.out.println(reply.getFrom() + ": " + reply.getMessage());

            }

            @Override
            public void onError(Throwable t) {
                log.error("gRPC error: ", t);
                System.out.println("Disconnected on error");
                System.exit(1);
            }

            @Override
            public void onCompleted() {
                System.out.println("Disconnected");
                System.exit(0);
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




    public void shutdown() throws InterruptedException {
        channel.shutdown().awaitTermination(5, TimeUnit.SECONDS);
    }



}
