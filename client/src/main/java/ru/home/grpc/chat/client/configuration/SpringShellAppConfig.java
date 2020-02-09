package ru.home.grpc.chat.client.configuration;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;


// will load shell_lib classes from library
@ComponentScan({"ru.geekbrains.dreamworkerln.spring2.shell_lib"})
@Configuration
public class SpringShellAppConfig {}
