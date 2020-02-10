package ru.home.grpc.chat.client.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.context.junit4.SpringRunner;

import javax.swing.*;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ExtendWith(SpringExtension.class)
class ChatClientTest {

    @Autowired
    private ChatClient chatClient;

    @Test
    void connect() throws Exception {
        chatClient.init("localhost", 8090, "qwerty", "1");
        chatClient.connect();

        TimeUnit.DAYS.sleep(365);
    }


//    @Configuration
//    @ComponentScan({"ru.home.grpc.chat.client.**", "ru.geekbrains.dreamworkerln.spring2.shell_lib.**"})
//    public static class SpringConfig {
//
//    }
}