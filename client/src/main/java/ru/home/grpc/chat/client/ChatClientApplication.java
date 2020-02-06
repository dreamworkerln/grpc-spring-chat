package ru.home.grpc.chat.client;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

import java.io.*;
import java.util.Scanner;

@SpringBootApplication
public class ChatClientApplication {

    private static BufferedReader bufferIn = new BufferedReader(new InputStreamReader(System.in));

	public static void fakeStdIn() {

        //try {
		// welcome to thread-unsafe glitches because no locks
		//InputStream stdin = System.in;
		//System.setIn(new ByteArrayInputStream("\n".getBytes()));
		//System.setIn(stdin);


            //bufferIn.read();
        //} catch (IOException e) {
        //    e.printStackTrace();
        //}

        //System.out.println("FAKEIT !!!!");
    }


    public static void main(String[] args) {

        SpringApplication.run(ChatClientApplication.class, args);
    }

}
