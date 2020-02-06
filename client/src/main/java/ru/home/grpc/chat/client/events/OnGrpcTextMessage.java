package ru.home.grpc.chat.client.events;

import org.springframework.context.ApplicationEvent;

public class OnGrpcTextMessage extends ApplicationEvent {
    private String textMessage;

    public OnGrpcTextMessage(Object source, String textMessage) {
        super(source);
        this.textMessage = textMessage;
    }

    public String getTextMessage() {
        return textMessage;
    }
}
