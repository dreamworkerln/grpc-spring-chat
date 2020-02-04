package ru.home.grpc.chat.server.service;

import com.google.protobuf.Timestamp;
import io.grpc.stub.StreamObserver;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.Assert;
import ru.home.grpc.chat.*;
import ru.home.grpc.chat.server.entities.Client;
import ru.home.grpc.chat.server.entities.ClientList;

import java.lang.invoke.MethodHandles;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import static org.apache.commons.codec.digest.MessageDigestAlgorithms.SHA_256;


public class ChatService extends ChatServiceGrpc.ChatServiceImplBase {



    private final static Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    //private static Set<StreamObserver<ServerReply>> clients = ConcurrentHashMap.newKeySet();

    private Map<String, Client> clientList = ClientList.INSTANCE.clientList;

    private SecureRandom secureRandom;

    private AtomicLong idAtomic = new AtomicLong();

    public ChatService() throws NoSuchAlgorithmException {

        secureRandom = SecureRandom.getInstance("NativePRNG");
    }

    @Override
    public void authenticate(AuthRequest request, StreamObserver<AuthResponse> responseObserver) {

        int result = 403;

        String login = request.getLogin();
        String password = request.getPassword();
        String id = "";

        log.info("Client '{}' authenticating", login);

        if (!StringUtils.isBlank(login)&&
            !StringUtils.isBlank(password) &&
            password.equals("1")) {

            result = 200;

            id = generateId();
            Client client = new Client(login, id);
            clientList.put(id, client);

            String msg = String.format("Client '%1$s' has logged in", client.getName());
            log.info(msg);

            broadcast(buildServerMessage("server", msg));
        }

        AuthResponse response = AuthResponse.newBuilder()
            .setResult(result)
            .setId(id)
            .build();
        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }


    // -------------------------------------------------------------------------------------------


    @Override
    public StreamObserver<ClientMessage> chat(StreamObserver<ServerMessage> responseObserver) {

        Client client = getClient();

        //log.info("New client '{}' connected to chat", client.getName());

        if (client.getResponseObserver() == null) {
            client.setResponseObserver(responseObserver);
        }

        return new StreamObserver<ClientMessage>() {

            @Override
            public void onNext(ClientMessage chatMessage) {

                Client client = getClient();

                log.info("From {}: {}", client.getName(), chatMessage.getMessage());

                ServerMessage serverMessage = buildServerMessage(client.getName(), chatMessage.getMessage());

                broadcast(serverMessage);
            }

            @Override
            public void onError(Throwable throwable) {

                log.error("gRPC error: ", throwable);

                Client client = getClient();
                clientList.remove(client.getId());

                String msg = String.format("Disconnected: '%1$s'", client.getName());
                log.info(msg);
                broadcast(buildServerMessage("server", msg));
            }

            @Override
            public void onCompleted() {

                Client client = getClient();
                clientList.remove(client.getId());

                String msg = String.format("Disconnected: '%1$s'", client.getName());
                log.info(msg);
                broadcast(buildServerMessage("server", msg));
            }

        };
    }


    private void broadcast(ServerMessage serverMessage) {
        // broadcast to all connected clients
        for (Map.Entry<String, Client> entry : clientList.entrySet()) {

            Client client = entry.getValue();

            if (client.getResponseObserver() == null) {
                continue;
            }
            client.getResponseObserver().onNext(serverMessage);
        }
    }


    private Client getClient() {

        String clientId = (String)HeaderInterceptor.USER_IDENTITY.get();
        Client client = clientList.get(clientId);
        Assert.notNull(client, "Unauthorized request, how ???");
        return client;
    }

    private ServerMessage buildServerMessage(String from, String message) {


        Instant now = Instant.now();
        long seconds = now.getEpochSecond();
        int  nanos = now.getNano();

        return ServerMessage.newBuilder()
            .setFrom(from)
            .setMessage(message)
            .setTimestamp(Timestamp.newBuilder()
                .setSeconds(seconds)
                .setNanos(nanos))
            .build();
    }


    private String generateId() {

        String id = Long.toString(idAtomic.getAndIncrement()) +
                    Long.toString(Instant.now().toEpochMilli()) +
                    Long.toString(secureRandom.nextLong());

        return new DigestUtils(SHA_256).digestAsHex(id);
    }


}
