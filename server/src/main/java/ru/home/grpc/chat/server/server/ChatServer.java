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

package ru.home.grpc.chat.server.server;

import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.ServerInterceptors;
import io.grpc.netty.NettyServerBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import ru.home.grpc.chat.server.service.ChatService;
import ru.home.grpc.chat.server.service.HeaderInterceptor;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.util.concurrent.TimeUnit;

/**
 * A sample gRPC server that serve the Chat (see chat.proto) service.
 */
@Service
public class ChatServer {
    private final static Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    @Value("${grpc.server.port:8090}")
    private final int port;

    private final Server server;

    public ChatServer() throws Exception {

        port = 8090;

        // https://github.com/grpc/grpc-java/issues/779
        // why implementing handmade keepalive
        // "But in short, on server you can't forcefully close a connection based on an RPC"
        // So going to kill idle unauthenticated connections using .maxConnectionIdle(...)
        // Or unauthenticated clients will send to server keepalive requests forever
        // this will happen when .permitKeepAliveWithoutCalls(true)
        // But without built-in or manual keepalive tcp connection will die on
        // NAT translation decay (idle TCP connection through NAT die in ~10 min)

        server = NettyServerBuilder
            .forPort(port)
            .permitKeepAliveWithoutCalls(true)
            .maxConnectionIdle(30, TimeUnit.SECONDS)
            .permitKeepAliveTime(5, TimeUnit.SECONDS)
            .addService(ServerInterceptors.intercept(new ChatService(), new HeaderInterceptor()))
            .build();
    }



    /** Start serving requests. */
    public void start() throws IOException {

        server.start();
        log.info("Server started, listening on " + port);

//        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
//            // Use stderr here since the logger may have been reset by its JVM shutdown hook.
//            System.err.println("*** shutting down gRPC server since JVM is shutting down");
//            ChatServer.this.stop();
//            System.err.println("*** server shut down");
//        }));
    }

    /** Stop serving requests and shutdown resources. */
    public void stop() {
        if (server != null) {
            server.shutdown();
        }
    }

    /**
     * Await termination on the main thread since the grpc library uses daemon threads.
     */
    private void blockUntilShutdown() throws InterruptedException {
        if (server != null) {
            server.awaitTermination();
        }
    }



    // ====================================================================================


    @PostConstruct
    public void postConstruct() throws IOException {
        this.start();
    }


    @PreDestroy
    public void destroy() {
        // Use stderr here since the logger may have been reset by its JVM shutdown hook.
        System.err.println("*** shutting down gRPC server since JVM is shutting down");
        ChatServer.this.stop();
        System.err.println("*** server shut down");

    }

}
