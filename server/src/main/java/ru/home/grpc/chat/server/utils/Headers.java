package ru.home.grpc.chat.server.utils;

import io.grpc.Context;
import io.grpc.Metadata;

import static io.grpc.Metadata.ASCII_STRING_MARSHALLER;

public class Headers {

    public static final String CLIENT_BASIC = "basic_auth";
    public static final String CLIENT_TOKEN = "token_auth";

    // header key basic auth
    public static final Metadata.Key<String> METADATA_KEY_CLIENT_BASIC =
        Metadata.Key.of(CLIENT_BASIC, ASCII_STRING_MARSHALLER);

    // header key token auth
    public static final Metadata.Key<String> METADATA_KEY_CLIENT_TOKEN =
        Metadata.Key.of(CLIENT_TOKEN, ASCII_STRING_MARSHALLER);

    // context key basic auth
    public static final Context.Key<Object> CLIENT_BASIC_CONTEXT_KEY =
        Context.key(CLIENT_BASIC);


    // context key token auth
    public static final Context.Key<Object> CLIENT_TOKEN_CONTEXT_KEY =
        Context.key(CLIENT_TOKEN);
}
