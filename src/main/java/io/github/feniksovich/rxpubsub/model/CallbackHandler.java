package io.github.feniksovich.rxpubsub.model;

import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public class CallbackHandler<T extends PubSubMessage> {

    protected final Class<T> callbackMessageClass;
    protected Consumer<T> handler;
    protected Consumer<Throwable> errorHandler;
    protected Runnable timeoutHandler;
    protected Runnable failureHandler;
    protected long timeoutThreshold = 2; // default timeout in seconds

    /**
     * Constructs a CallbackHandler for the specified {@link PubSubMessage} class.
     *
     * @param callbackMessageClass the class of the PubSubMessage to handle callback for
     */
    public CallbackHandler(Class<T> callbackMessageClass) {
        this.callbackMessageClass = callbackMessageClass;
    }

    protected CallbackHandler(CallbackHandler<T> callbackHandler) {
        handler = callbackHandler.handler;
        errorHandler = callbackHandler.errorHandler;
        timeoutHandler = callbackHandler.timeoutHandler;
        failureHandler = callbackHandler.failureHandler;
        timeoutThreshold = callbackHandler.timeoutThreshold;
        callbackMessageClass = callbackHandler.callbackMessageClass;
    }

    /**
     * Sets the handler for processing the callback message.
     *
     * @param handler the consumer function to handle the callback message
     * @return the CallbackHandler instance for chaining purposes
     */
    public CallbackHandler<T> handle(Consumer<T> handler) {
        this.handler = handler;
        return this;
    }

    /**
     * Sets the error handler for handling errors that occur during
     * source message publishing or callback processing.
     *
     * @param handler the consumer function to handle errors
     * @return the CallbackHandler instance for chaining purposes
     */
    public CallbackHandler<T> handleError(Consumer<Throwable> handler) {
        errorHandler = handler;
        return this;
    }

    /**
     * Sets the timeout handler for handling timeout of callback awaiting.
     *
     * @param handler the runnable to handle timeouts
     * @return the CallbackHandler instance for chaining purposes
     */
    public CallbackHandler<T> handleTimeout(Runnable handler) {
        timeoutHandler = handler;
        return this;
    }

    /**
     * Sets the failure handler for handling errors and timeouts.
     * This handler will trigger if an error occurs or the callback timeout expires.
     *
     * @param handler the runnable to handle failures
     * @return the CallbackHandler instance for chaining purposes
     */
    public CallbackHandler<T> handleFailure(Runnable handler) {
        failureHandler = handler;
        return this;
    }

    /**
     * Sets the timeout threshold for callback processing.
     *
     * @param timeout the timeout value
     * @param unit    the time unit of the timeout value
     * @return the CallbackHandler instance for chaining purposes
     * @throws IllegalArgumentException if the timeout value is zero or negative
     */
    public CallbackHandler<T> setTimeout(long timeout, TimeUnit unit) {
        if (timeout <= 0) {
            throw new IllegalArgumentException("timeout cannot be zero or negative");
        }
        this.timeoutThreshold = TimeUnit.SECONDS.convert(timeout, unit);
        return this;
    }
}
