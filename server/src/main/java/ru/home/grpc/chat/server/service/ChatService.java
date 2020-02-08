package ru.home.grpc.chat.server.service;

import com.google.protobuf.Timestamp;
import io.grpc.stub.ClientCallStreamObserver;
import io.grpc.stub.ClientResponseObserver;
import io.grpc.stub.StreamObserver;
import org.apache.commons.codec.digest.DigestUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.Assert;
import ru.home.grpc.chat.*;
import ru.home.grpc.chat.server.entities.Client;
import ru.home.grpc.chat.server.entities.ClientList;
import ru.home.grpc.chat.server.utils.Credentials;

import javax.annotation.Nullable;
import java.lang.invoke.MethodHandles;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;

import static org.apache.commons.codec.digest.MessageDigestAlgorithms.SHA_256;
import static ru.home.grpc.chat.server.utils.Headers.CLIENT_BASIC_CONTEXT_KEY;
import static ru.home.grpc.chat.server.utils.Headers.CLIENT_TOKEN_CONTEXT_KEY;


public class ChatService extends ChatServiceGrpc.ChatServiceImplBase {

    private final static Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    // Contains all current connected clients (singleton)
    private Map<String, Client> clientList = ClientList.INSTANCE.clientList;

    private SecureRandom secureRandom;

    private AtomicLong idAtomic = new AtomicLong();

    private ReentrantLock lock = new ReentrantLock();

    public ChatService() throws NoSuchAlgorithmException {

        secureRandom = SecureRandom.getInstance("NativePRNG");
    }


    @Override
    public void authenticate(AuthRequest request, StreamObserver<AuthResponse> responseObserver) {

        Credentials credentials = getCredentials();
        String login = credentials.getLogin();

        // multiply login from same account enabled
        // will allow simultaneously authenticate several clients with same login/password
        // to prevent this need to have index on client.login
        String token = generateToken();

        Client client = new Client(login, token);
        clientList.put(token, client);

        //log.info("Client '{}' has authenticated", login);

        AuthResponse response = AuthResponse.newBuilder()
            .setToken(token)
            .build();
        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }


    // -------------------------------------------------------------------------------------------


    @Override
    public StreamObserver<ClientMessage> chat(StreamObserver<ServerMessage> responseObserver) {

        Client client = getClient();

        Assert.isNull(client.getResponseObserver(), "client.getResponseObserver() != null");
        client.setResponseObserver(responseObserver);

        String msg = String.format("Client '%1$s' has entered the chat", client.getLogin());
        log.info(msg);
        broadcast(buildServerMessage("server", msg));


        return new StreamObserver<ClientMessage>() {

            @Override
            public void onNext(ClientMessage chatMessage) {

                Client client = getClient();

                log.info("From {}: {}", client.getLogin(), chatMessage.getMessage());

                ServerMessage serverMessage = buildServerMessage(client.getLogin(), chatMessage.getMessage());

                broadcast(serverMessage);
            }

            @Override
            public void onError(Throwable throwable) {

                log.debug("gRPC error: ", throwable);

                Client client = getClient();
                clientList.remove(client.getToken());

                String msg = String.format("Disconnected: '%1$s'", client.getLogin());
                log.info(msg);
                broadcast(buildServerMessage("server", msg));
            }

            @Override
            public void onCompleted() {

                Client client = getClient();
                clientList.remove(client.getToken());

                String msg = String.format("Disconnected: '%1$s'", client.getLogin());
                log.info(msg);
                broadcast(buildServerMessage("server", msg));
            }

        };
    }

    @Override
    public StreamObserver<PingMessage> ping(StreamObserver<PingMessage> responseObserver) {

        return new StreamObserver<PingMessage>() {

            @Override
            public void onNext(PingMessage value) {

                log.trace("PING IN ack:{}", value.getAck());
                PingMessage pong = PingMessage.newBuilder()
                    .setAck(true)
                    .build();
                log.debug("PING OUT ack:{}", pong.getAck());
                responseObserver.onNext(pong);
            }

            @Override
            public void onError(Throwable t) {}

            @Override
            public void onCompleted() {}
        };


    }


    // --------------------------------------------------------------------------------------

    private void broadcast(ServerMessage serverMessage) {

        // StreamObserver not thread-safe

        try {
            lock.lock();

            // Synchronized broadcast to all connected clients
            for (Map.Entry<String, Client> entry : clientList.entrySet()) {

                Client client = entry.getValue();

                if (client.getResponseObserver() == null) {
                    continue;
                }
                client.getResponseObserver().onNext(serverMessage);
            }
        }
        finally {
            lock.unlock();
        }
    }


    private Credentials getCredentials() {

        Credentials result;
        result =  (Credentials)CLIENT_BASIC_CONTEXT_KEY.get();
        Assert.notNull(result, "credentials == null");
        return result;
    }


    /**
     * get client from Context
     * @return @NotNull client
     */
    private Client getClient() {
        String token = (String)CLIENT_TOKEN_CONTEXT_KEY.get();
        Client client = clientList.get(token);
        Assert.notNull(client, "client == null");
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


    private String generateToken() {

        String id = Long.toString(idAtomic.getAndIncrement()) +
                    Long.toString(Instant.now().toEpochMilli()) +
                    Long.toString(secureRandom.nextLong());

        return new DigestUtils(SHA_256).digestAsHex(id);
    }


}

//private static Set<StreamObserver<ServerReply>> clients = ConcurrentHashMap.newKeySet();
