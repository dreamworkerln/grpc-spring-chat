package ru.home.grpc.chat.client.commands;

import ru.home.grpc.chat.ServerMessage;

public interface ClientEvents {

    void onMessage(ServerMessage message);

    void onInfo(String message);

    void onError(Throwable t);

    void onOpen(String message);

    void onClose();
}
