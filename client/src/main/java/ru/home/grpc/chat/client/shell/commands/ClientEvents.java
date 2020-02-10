package ru.home.grpc.chat.client.shell.commands;

import io.grpc.ConnectivityState;
import ru.home.grpc.chat.ServerMessage;

public interface ClientEvents {

    void onMessage(ServerMessage message);

    void onError(Throwable t);

    void onStateChanged(ConnectivityState previous, ConnectivityState current);
}
