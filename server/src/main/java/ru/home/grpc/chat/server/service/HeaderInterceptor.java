package ru.home.grpc.chat.server.service;

import io.grpc.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;
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

    private static final String CLIENT_BASIC = "basic_auth";
    private static final String CLIENT_TOKEN = "token_auth";

    // header key basic auth
    private static final Metadata.Key<String> METADATA_KEY_CLIENT_BASIC =
        Metadata.Key.of(CLIENT_BASIC, ASCII_STRING_MARSHALLER);

    // header key token auth
    private static final Metadata.Key<String> METADATA_KEY_CLIENT_TOKEN =
        Metadata.Key.of(CLIENT_TOKEN, ASCII_STRING_MARSHALLER);


    // context key token auth
    public static final Context.Key<Object> CLIENT_TOKEN_CONTEXT_KEY =
        Context.key(CLIENT_TOKEN);

    private ConcurrentMap<String, Client> clientList = ClientList.INSTANCE.clientList;


    /**
     *  Authenticate, using login/password or TOKEN
     */
    @Override
    public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
        ServerCall<ReqT, RespT> call,
        Metadata headers,
        ServerCallHandler<ReqT, RespT> next) {

        boolean authenticated = false;

        log.info("incoming call: {}", headers.toString());

        Credentials credentials = null;

        // BASIC AUTH -------------------------------------------------

        authenticated =
            (credentials = Credentials.getCredentials(headers)) != null ||
            (token = Credentials.getCredentials(headers)) != null




        if (credentials!= null) {

            authenticated = true;
        }

        // TOKEN AUTH -------------------------------------------------
        else {

        }



        String token = getClientToken(headers);

        // add header value to context
        Context context = Context.current().withValue(CLIENT_TOKEN_CONTEXT_KEY, identity);

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



    private static class Credentials {

        private String login;
        private String password;

        public Credentials(String login, String password) {
            this.login = login;
            this.password = password;
        }

        public String getLogin() {
            return login;
        }

        public void setLogin(String login) {
            this.login = login;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }

        public static Credentials getCredentials(Metadata headers) {

            Credentials result = null;

            String basicString = null;
            if (headers.containsKey(METADATA_KEY_CLIENT_BASIC)) {
                basicString = headers.get(METADATA_KEY_CLIENT_BASIC);
                basicString = StringUtils.isEmpty(basicString) ? null : basicString;
            }

            if(basicString != null) {
                String[] credentials = basicString.split(":");

                if (credentials.length == 2) {
                    result = new Credentials(credentials[0], credentials[1]);
                }
            }
            return result;
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
