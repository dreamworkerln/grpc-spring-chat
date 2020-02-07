package ru.home.grpc.chat.server.entities;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;


public enum ClientList {
    INSTANCE;
    public ConcurrentMap<String, Client> clientList = new ConcurrentHashMap<>();

}
