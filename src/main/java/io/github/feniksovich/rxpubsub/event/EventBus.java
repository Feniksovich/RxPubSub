package io.github.feniksovich.rxpubsub.event;

import io.github.feniksovich.rxpubsub.model.PubSubChannel;
import io.github.feniksovich.rxpubsub.model.PubSubMessage;

import java.util.function.Consumer;
import java.util.function.Function;

/**
 * The RxPubSub event bus.
 * Used to listen to the reception of pubsub messages.
 */
public interface EventBus {
    /**
     * Registers a new listener for the specified incoming message.
     * Provided handler guaranteed to be processed asynchronously in
     * a dedicated event bus thread pool.
     *
     * @param messageClass the pubsub message class
     * @param handler the reception handler
     * @return {@link EventBus} instance for chaining purposes
     */
    <T extends PubSubMessage> EventBus registerReceiptListener(Class<T> messageClass, Consumer<T> handler);

    /**
     * Registers a new listener for the specified incoming message.
     * The listener returns a certain {@link PubSubMessage} as a callback
     * and sends it to the specified channel.
     * The provided handler is guaranteed to be processed asynchronously
     * in a dedicated event bus thread pool.
     *
     * @param messageClass the pubsub message class
     * @param channel the channel to send the callback to
     * @param handler the reception handler that returns {@link PubSubMessage} as a callback
     * @return {@link EventBus} instance for chaining purposes
     */
    <T extends PubSubMessage> EventBus registerRespondingListener(Class<T> messageClass, PubSubChannel channel, Function<T, PubSubMessage> handler);
}
