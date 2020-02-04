package ru.home.grpc.chat.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;
import ru.home.grpc.chat.client.service.ChatClient;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.invoke.MethodHandles;


@Component
public class ClientStartupRunner implements ApplicationRunner {

    private static BufferedReader bufferIn;

    private final static Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    private final ApplicationContext context;

    public ClientStartupRunner(ApplicationContext context) {
        this.context = context;
    }


    @Override
    public void run(ApplicationArguments args) throws Exception {

        bufferIn = new BufferedReader(new InputStreamReader(System.in));

        System.out.print("Enter you login: ");
        String login = readLine();

        System.out.print("Enter you password: ");
        String password = readLine();


        //String name = "123";




        log.info("Connecting to server ...");
        ChatClient client = context.getBean(ChatClient.class, "localhost", 8090, login, password);

        client.start();

        while(true) {
            String text = readLine();
            client.sendMessage(text);
        }

        //log.info("Disconnecting ...");
        //client.shutdown();
    }


    private static String readLine() {

        String result = null;
        try {
            result = bufferIn.readLine();
        }
        catch (IOException ignore) {}
        return result;
    }
}