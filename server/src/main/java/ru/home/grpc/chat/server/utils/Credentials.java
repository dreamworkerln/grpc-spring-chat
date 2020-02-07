package ru.home.grpc.chat.server.utils;

import io.grpc.Metadata;
import org.springframework.util.StringUtils;
import ru.home.grpc.chat.server.service.HeaderInterceptor;

public class Credentials {

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
        if (headers.containsKey(Headers.METADATA_KEY_CLIENT_BASIC)) {
            basicString = headers.get(Headers.METADATA_KEY_CLIENT_BASIC);
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
