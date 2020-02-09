package ru.home.grpc.chat.server.entities;

import io.grpc.stub.StreamObserver;
import ru.home.grpc.chat.ServerMessage;

public class Client {


    String login;
    String token;

    StreamObserver<ServerMessage> chatObserver;

    public Client(String login, String token) {
        this.login = login;
        this.token = token;
    }

    public StreamObserver<ServerMessage> getChatObserver() {
        return chatObserver;
    }

    public void setChatObserver(StreamObserver<ServerMessage> chatObserver) {
        this.chatObserver = chatObserver;
    }

    public String getLogin() {
        return login;
    }

    public void setLogin(String login) {
        this.login = login;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }
}
