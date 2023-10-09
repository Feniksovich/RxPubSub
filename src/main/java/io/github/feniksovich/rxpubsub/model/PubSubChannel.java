package io.github.feniksovich.rxpubsub.model;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Represents a pubsub channel.
 * A channel is identified by a namespace and a name.
 */
public class PubSubChannel {

    private static final Pattern VALID_IDENTIFIER_REGEX = Pattern.compile("[a-z0-9/\\-_]*");

    private final String namespace;
    private final String name;

    private PubSubChannel(String namespace, String name) {
        this.namespace = namespace;
        this.name = name;
    }

    /**
     * Creates a new PubSubChannel instance with the specified namespace and name.
     *
     * @param namespace the namespace of the channel
     * @param name the name of the channel
     * @return a new PubSubChannel instance
     * @throws IllegalArgumentException if the namespace or name is null or empty, or if they are not valid
     */
    public static PubSubChannel from(String namespace, String name) {
        Preconditions.checkArgument(!Strings.isNullOrEmpty(namespace), "namespace is null or empty");
        Preconditions.checkArgument(!Strings.isNullOrEmpty(name), "name is null or empty");
        Preconditions.checkArgument(
                VALID_IDENTIFIER_REGEX.matcher(namespace).matches(),
                "namespace is not valid"
        );
        Preconditions.checkArgument(
                VALID_IDENTIFIER_REGEX.matcher(name).matches(),
                "name is not valid"
        );
        return new PubSubChannel(namespace, name);
    }

    /**
     * Gets the ID of the pubsub channel.
     *
     * @return the ID of the channel in the format "namespace:name"
     */
    public String getId() {
        return namespace + ":" + name;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PubSubChannel that = (PubSubChannel) o;
        return Objects.equals(namespace, that.namespace) && Objects.equals(name, that.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(namespace, name);
    }

    @Override
    public String toString() {
        return "PubSubChannel{" +
                "namespace='" + namespace + '\'' +
                ", name='" + name + '\'' +
                '}';
    }
}
