package ru.home.grpc.chat.server.entities;

import io.grpc.stub.StreamObserver;
import ru.home.grpc.chat.ServerMessage;

public class Client {


    String name;
    String id;

    StreamObserver<ServerMessage> responseObserver;

    public Client(String name, String id) {
        this.name = name;
        this.id = id;
    }

    public StreamObserver<ServerMessage> getResponseObserver() {
        return responseObserver;
    }

    public void setResponseObserver(StreamObserver<ServerMessage> responseObserver) {
        this.responseObserver = responseObserver;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }
}
