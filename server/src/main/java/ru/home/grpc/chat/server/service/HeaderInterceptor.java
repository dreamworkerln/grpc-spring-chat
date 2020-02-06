package ru.home.grpc.chat.server.service;

import io.grpc.*;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.home.grpc.chat.server.entities.Client;
import ru.home.grpc.chat.server.entities.ClientList;

import java.lang.invoke.MethodHandles;
import java.util.concurrent.ConcurrentMap;

import static io.grpc.Metadata.ASCII_STRING_MARSHALLER;



// use from
// https://stackoverflow.com/questions/40112374/how-do-i-access-request-metadata-for-a-java-grpc-service-i-am-defining


/**
 * "Authentication" emulator (require user name to participate in chat)
 * <br> ToDo:need password
 *
 */
public class HeaderInterceptor implements ServerInterceptor {

    private final static Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    private static final String CLIENT_ID = "client_id";
    private static final Metadata.Key<String> CLIENT_ID_KEY = Metadata.Key.of(CLIENT_ID, ASCII_STRING_MARSHALLER);

    public static final Context.Key<Object> USER_IDENTITY = Context.key(CLIENT_ID);

    private ConcurrentMap<String, Client> clientList = ClientList.INSTANCE.clientList;

    @Override
    public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
        ServerCall<ReqT, RespT> call,
        Metadata headers,
        ServerCallHandler<ReqT, RespT> next) {

        boolean authenticated = false;


        log.info("incoming call: {}", headers.toString());

        String identity = getClientId(headers);

        // add header value to context
        Context context = Context.current().withValue(USER_IDENTITY, identity);

        String[] methodList = call.getMethodDescriptor().getFullMethodName().split("/");

        if (methodList.length >=2) {

            // get rpc method name
            String method = methodList[1];

            authenticated = method.equals("authenticate") || clientList.containsKey(identity);
        }



        // Only rpc "greet" allow anonymous access
        if (authenticated) {
            return Contexts.interceptCall(context, call, headers, next);
        }
        // Assume user not authenticated
        else {
            log.info("Client not authenticated");
            call.close(Status.PERMISSION_DENIED.withDescription("invalid login/password"), new Metadata());
            //noinspection unchecked
            return new ServerCall.Listener() {};
        }
    }


    private String getClientId(Metadata headers) {

        String result = null;

        if (headers.containsKey(CLIENT_ID_KEY)) {

            result = headers.get(CLIENT_ID_KEY);
            result = StringUtils.isBlank(result) ? null : result;
        }

        return result;
    }

}
