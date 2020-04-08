package ru.home.grpc.chat.server;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

import java.lang.invoke.MethodHandles;

@SpringBootApplication
@EnableScheduling
public class ChatServerApplication {

    // -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=7005 -Dcom.sun.management.jmxremote.port=7006 -Dcom.sun.management.jmxremote.rmi.port=7006 -Dcom.sun.management.jmxremote.local.only=true -Dcom.sun.management.jmxremote.host=localhost -Dcom.sun.management.jmxremote.authenticate=false -Djava.rmi.server.hostname=127.0.0.1 -Dcom.sun.management.jmxremote.ssl=false

    private final static Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    public static void main(String[] args) {

        SpringApplication.run(ChatServerApplication.class, args);
    }

    // Stub to prevent spring boot application from closing
    @Scheduled(fixedDelay = 3600)
    public void doNothing() {
        // Forces Spring Scheduling managing thread to start
        // and prevent Spring Application to shut down
    }

}
