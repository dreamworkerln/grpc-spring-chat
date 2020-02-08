package ru.home.grpc.chat.client.configuration;

import org.jline.reader.History;
import org.jline.reader.LineReader;
import org.jline.reader.impl.history.DefaultHistory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.EventListener;

import java.io.IOException;
import java.nio.file.Paths;

@Configuration
public class SpringShellHistoryConfiguration {

    private History history;


    @Autowired
    @Lazy
    public void setHistory(History history) {
        this.history = history;
    }

    @Bean
    public History history(LineReader lineReader, @Value("${app.spring.shell.history.file:spring-shell.log}") String historyPath) {
        lineReader.setVariable(LineReader.HISTORY_FILE, Paths.get(historyPath));
        return new DefaultHistory(lineReader);
    }

    @EventListener
    public void onContextClosedEvent(ContextClosedEvent event) throws IOException {
        history.save();
    }

}
