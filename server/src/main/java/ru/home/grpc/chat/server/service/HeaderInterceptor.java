package ru.home.grpc.chat.server.service;

import io.grpc.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;
import ru.home.grpc.chat.server.entities.Client;
import ru.home.grpc.chat.server.entities.ClientList;
import ru.home.grpc.chat.server.utils.Credentials;
import ru.home.grpc.chat.server.utils.Headers;

import java.lang.invoke.MethodHandles;
import java.util.Map;
import java.util.concurrent.ConcurrentMap;

import static io.grpc.Metadata.ASCII_STRING_MARSHALLER;
import static ru.home.grpc.chat.server.utils.Headers.CLIENT_BASIC_CONTEXT_KEY;
import static ru.home.grpc.chat.server.utils.Headers.CLIENT_TOKEN_CONTEXT_KEY;
import static ru.home.grpc.chat.server.utils.Headers.METADATA_KEY_CLIENT_TOKEN;


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

        log.info("incoming call: {}", headers.toString());

        Credentials credentials;
        String token;
        Context context = Context.current(); // like session bean

        // BASIC AUTH -------------------------------------------------

        if ((credentials = Credentials.getCredentials(headers)) != null) {

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
        }

        // TOKEN AUTH -------------------------------------------------

        if (!authenticated && (token = getClientToken(headers)) != null) {

            if (clientList.containsKey(token)) {

                authenticated = true;

                // add token to context
                context = Context.current().withValue(CLIENT_TOKEN_CONTEXT_KEY, token);
            }
        }

        // Authenticated user
        if (authenticated) {
            log.info("Client authentication successful");
            Assert.notNull(context, "context == null");
            return Contexts.interceptCall(context, call, headers, next);
        }
        // Not authenticated
        else {
            log.info("Client not authenticated");
            call.close(Status.UNAUTHENTICATED .withDescription("not authenticated"), new Metadata());
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

}



/*
        String[] methodList = call.getMethodDescriptor().getFullMethodName().split("/");
        if (methodList.length >=2) {

            // get rpc method name
            String method = methodList[1];

            authenticated = method.equals("authenticate") || clientList.containsKey(identity);
        }
 */