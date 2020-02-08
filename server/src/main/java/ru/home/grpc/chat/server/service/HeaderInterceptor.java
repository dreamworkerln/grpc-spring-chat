package ru.home.grpc.chat.server.service;

import io.grpc.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;
import ru.home.grpc.chat.server.entities.Client;
import ru.home.grpc.chat.server.entities.ClientList;
import ru.home.grpc.chat.server.utils.Credentials;


import java.lang.invoke.MethodHandles;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static io.grpc.Metadata.ASCII_STRING_MARSHALLER;
import static ru.home.grpc.chat.server.utils.Headers.*;


// use from
// https://stackoverflow.com/questions/40112374/how-do-i-access-request-metadata-for-a-java-grpc-service-i-am-defining


/**
 * "Authentication" emulator (require user name to participate in chat)
 * <br> ToDo:need password
 *
 */
public class HeaderInterceptor implements ServerInterceptor {

    private final static Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    // Contains all current connected clients (singleton)
    private Map<String, Client> clientList = ClientList.INSTANCE.clientList;

    /**
     *  Authenticate, using login/password or TOKEN
     */
    @Override
    public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(ServerCall<ReqT, RespT> call,
                                                                 Metadata headers,
                                                                 ServerCallHandler<ReqT, RespT> next) {
        boolean authenticated = false;

        String methodName = getMethodName(call);

        // Comment out on release - reveal passwords
        log.info("DISABLE ME ON PRODUCTION! Incoming call: {}, rpc: {}", headers, methodName);

        // Will reduce performance, disable on production
        // logIncomingCall(headers);

        Credentials credentials;
        String token;
        Context context = Context.current(); // like session bean

        // BASIC AUTH -------------------------------------------------

        if ((credentials = Credentials.getCredentials(headers)) != null) {

            log.info("Authenticating client: '{}'", credentials.getLogin());

            //ToDo: implement authentication service that contains bcrypted passwords
            // to compare with

            // DEMO: Authenticate with any login and password=1
            if (credentials.getPassword().equals("1")) {
                authenticated = true;

                // clear password
                credentials.setPassword(null);

                // add basicAuth credentials to context
                context = Context.current().withValue(CLIENT_BASIC_CONTEXT_KEY, credentials);

            }

            log.info("Client '{}' has authenticated", credentials.getLogin());
        }

        // TOKEN AUTH -------------------------------------------------

        if (!authenticated && (token = getClientToken(headers)) != null) {

            if (clientList.get(token) != null) {

                authenticated = true;

                // add token to context
                context = Context.current().withValue(CLIENT_TOKEN_CONTEXT_KEY, token);
            }
        }

        // Authenticated user
        if (authenticated) {
            Assert.notNull(context, "context == null");
            return Contexts.interceptCall(context, call, headers, next);
        }
        // Not authenticated
        else {
            log.info("Client not authenticated");
            call.close(Status.UNAUTHENTICATED .withDescription("Not authenticated"), new Metadata());
            //call.close(Status.PERMISSION_DENIED.withDescription("not authenticated"), new Metadata.Trailers());
            //noinspection unchecked
            return new ServerCall.Listener() {};
        }
    }


    private String getClientToken(Metadata headers) {

        String result = null;

        if (headers.containsKey(METADATA_KEY_CLIENT_TOKEN)) {

            result = headers.get(METADATA_KEY_CLIENT_TOKEN);
            result = StringUtils.isEmpty(result) ? null : result;
        }

        return result;
    }



    private <ReqT, RespT> void logIncomingCall(ServerCall<ReqT, RespT> call, Metadata headers) {
        // cleanup passwords
        Set<String> filteredKeys = new HashSet<>(headers.keys());
        filteredKeys.removeIf(s -> s.equals(CLIENT_BASIC) || s.equals(CLIENT_TOKEN));

        // ASCII_STRING_MARSHALLER only, no binary headers supported
        StringBuilder sb = new StringBuilder("Metadata=(");
        boolean fmt = false;
        for (String key : filteredKeys) {
            if (fmt) sb.append(","); else fmt = true; // formatting
            sb.append(key).append("=");
            Iterable<String> values = headers.getAll(Metadata.Key.of(key, ASCII_STRING_MARSHALLER));
            for (String s : Objects.requireNonNull(values)) {
                sb.append(s);
            }
        }
        sb.append(')');

        log.info("Incoming call: {}, rpc: {}", sb.toString(), getMethodName(call));
    }

    private <ReqT, RespT> String getMethodName(ServerCall<ReqT, RespT> call) {

        String result = null;

        String[] methodList = call.getMethodDescriptor().getFullMethodName().split("/");
        if (methodList.length >=2) {

            // get rpc method name
            result = methodList[1];
        }
        return result;
    }

    // ----------------------------------------------------------------------------
}
