package io.github.feniksovich.rxpubsub.misc;

import io.lettuce.core.RedisURI;

import java.time.Duration;
import java.util.Objects;

public class MessagingServiceConfig {

    private final String host;
    private final int port;
    private final String username;
    private final String password;
    private final int queryTimeoutMillis;

    private static final int DEFAULT_TIMEOUT_MILLIS = 2000;

    public MessagingServiceConfig(String host, int port, String username, String password) {
        this(host, port, username, password, DEFAULT_TIMEOUT_MILLIS, DEFAULT_TIMEOUT_MILLIS);
    }

    public MessagingServiceConfig(String host, int port, String username, String password, int callbackTimeoutMillis) {
        this(host, port, username, password, callbackTimeoutMillis, DEFAULT_TIMEOUT_MILLIS);
    }

    public MessagingServiceConfig(
            String host, int port, String username, String password,
            int callbackTimeoutMillis, int queryTimeoutMillis
    ) {
        this.host = host;
        if (port < 0 || port > 65536)
            throw new IllegalArgumentException("Invalid port " + port + ": must be in range [1:65535]");
        this.port = port;
        this.username = username;
        this.password = password;
        this.queryTimeoutMillis = queryTimeoutMillis > 0 && queryTimeoutMillis <= 30000 ?
                queryTimeoutMillis : DEFAULT_TIMEOUT_MILLIS;
    }

    public int getQueryTimeoutMillis() {
        return queryTimeoutMillis;
    }

    public RedisURI connectionUri() {
        return RedisURI.builder()
                .withHost(host)
                .withPort(port)
                .withAuthentication(username, password)
                .withTimeout(Duration.ofMillis(queryTimeoutMillis))
                .build();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        MessagingServiceConfig that = (MessagingServiceConfig) o;
        return port == that.port && queryTimeoutMillis == that.queryTimeoutMillis && Objects.equals(host, that.host) && Objects.equals(username, that.username) && Objects.equals(password, that.password);
    }

    @Override
    public int hashCode() {
        return Objects.hash(host, port, username, password, queryTimeoutMillis);
    }
}
