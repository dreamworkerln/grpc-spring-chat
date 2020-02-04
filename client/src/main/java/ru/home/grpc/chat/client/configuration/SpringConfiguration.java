package ru.home.grpc.chat.client.configuration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;
import ru.home.grpc.chat.client.service.ChatClient;

@Configuration
public class SpringConfiguration {

    @Bean
    @Scope(value = "prototype")
    @SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection")
    public ChatClient getClient(String host, int port, String login, String password) {
        return new ChatClient(host, port, login, password);
    }
}

