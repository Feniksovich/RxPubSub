package io.github.feniksovich.rxpubsub;

import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import io.github.feniksovich.rxpubsub.annotations.AnnotationUtils;
import io.github.feniksovich.rxpubsub.event.EventBus;
import io.github.feniksovich.rxpubsub.event.EventBusImpl;
import io.github.feniksovich.rxpubsub.misc.MessagingServiceConfig;
import io.github.feniksovich.rxpubsub.misc.ReservedFields;
import io.github.feniksovich.rxpubsub.model.CallbackHandler;
import io.github.feniksovich.rxpubsub.model.CallbackHandlerWrapper;
import io.github.feniksovich.rxpubsub.model.PubSubChannel;
import io.github.feniksovich.rxpubsub.model.PubSubMessage;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisConnectionException;
import io.lettuce.core.pubsub.RedisPubSubAdapter;
import io.lettuce.core.pubsub.StatefulRedisPubSubConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.ConnectException;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public final class MessagingService {

    private MessagingServiceConfig config;
    private RedisClient redisClient;
    private StatefulRedisPubSubConnection<String, String> subConnection;
    private StatefulRedisPubSubConnection<String, String> pubConnection;

    private final Logger logger;
    private final EventBusImpl eventBus;

    private final ConcurrentHashMap<String, Class<? extends PubSubMessage>> registeredMessages = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, CallbackHandlerWrapper<? extends PubSubMessage>> awaitingCallbacks = new ConcurrentHashMap<>();

    private final ScheduledThreadPoolExecutor timeoutsScheduler = new ScheduledThreadPoolExecutor(20);

    private final ConcurrentHashMap<String, PubSubChannel> registeredIncomingChannels = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, PubSubChannel> registeredOutgoingChannels = new ConcurrentHashMap<>();

    private static final Gson GSON = new GsonBuilder()
            .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
            .disableHtmlEscaping()
            .create();

    public MessagingService(MessagingServiceConfig config) {
        this(config, LoggerFactory.getLogger(MessagingService.class.getName()));
    }
    
    public MessagingService(MessagingServiceConfig config, Logger logger) {
        this.config = config;
        this.eventBus = new EventBusImpl(this);
        this.logger = logger;
        timeoutsScheduler.setRemoveOnCancelPolicy(true);
        timeoutsScheduler.setKeepAliveTime(1, TimeUnit.SECONDS);
        init();
    }

    private boolean init() {
        redisClient = RedisClient.create(config.connectionUri());
        try {
            subConnection = redisClient.connectPubSub();
            pubConnection = redisClient.connectPubSub();
            subConnection.addListener(new RedisPubSubAdapter<>() {
                @Override
                public void message(String channel, String message) {
                    handleMessage(channel, message);
                }
            });
        } catch (RedisConnectionException ex) {
            logger.error("Failed to connect to the Redis server", ex);
            return false;
        }
        return true;
    }

    private void handleMessage(String channel, String message) {
        // Don't handle if channel isn't registered as incoming
        if (!registeredIncomingChannels.containsKey(channel)) return;

        logger.debug("Received message: " + channel + " @ " + message);

        // Check whether any message is registered with the obtained class ID
        getMessageClass(message).ifPresentOrElse(clazz -> {
            // Deserialize raw JSON message to pubsub message POJO
            var pubSubMessage = GSON.fromJson(message, clazz);
            if (pubSubMessage.getResponseTo().isPresent()) {
                var awaitingCallback = awaitingCallbacks.get(pubSubMessage.getResponseTo().get());
                if (awaitingCallback != null) {
                    awaitingCallbacks.remove(pubSubMessage.getMessageId());
                    awaitingCallback.accept(clazz, pubSubMessage);
                }
                return;
            }
            // There is no awaiting callbacks, pass pubsub message to the event bus
            eventBus.handleMessage(clazz, pubSubMessage);
        }, () -> logger.debug("Received message is unregistered. Rejected."));
    }

    /**
     * Determines the class based on the raw string representation of the message.
     *
     * @param message a string representation of the message
     * @return the class of the message if registered, otherwise nothing
     */
    private Optional<Class<? extends PubSubMessage>> getMessageClass(String message) {
        var id = GSON.fromJson(message, JsonObject.class).get(ReservedFields.MESSAGE_CLASS_IDENTIFIER_FIELD);
        if (id == null) return Optional.empty();
        return Optional.ofNullable(registeredMessages.get(id.getAsString()));
    }

    /**
     * Sets the class id for the message.
     *
     * @param jsonMessage target message {@link JsonObject} representation
     * @param classId id to set
     * @return {@link JsonObject} message representation with set class id
     */
    private JsonObject setMessageClass(JsonObject jsonMessage, String classId) {
        jsonMessage.addProperty(ReservedFields.MESSAGE_CLASS_IDENTIFIER_FIELD, classId);
        return jsonMessage;
    }

    private void subscribe(PubSubChannel channel) {
        Objects.requireNonNull(channel, "channel is null");
        subConnection.sync().subscribe(channel.getId());
    }

    private void subscribe(String... channelsId) {
        Objects.requireNonNull(channelsId, "channelsId is null");
        subConnection.sync().subscribe(channelsId);
    }

    private void unsubscribe(PubSubChannel channel) {
        Objects.requireNonNull(channel, "channel is null");
        subConnection.sync().unsubscribe(channel.getId());
    }

    private void unsubscribe(String... channelsId) {
        Objects.requireNonNull(channelsId, "channelsId is null");
        subConnection.sync().unsubscribe(channelsId);
    }

    /**
     * Reloads the messaging service by completely destroying the current client
     * and constructing a new one using the assigned {@link MessagingServiceConfig}.
     * This operation resubscribes to all registered channels.
     *
     * @return {@code true} if the reload is successful, {@code false} otherwise
     */
    public boolean reload() {
        redisClient.shutdown();

        final boolean state = init();
        if (state && !registeredOutgoingChannels.isEmpty())
            subscribe(registeredOutgoingChannels.keySet().toArray(new String[0]));

        return state;
    }

    /**
     * Reloads the messaging service by completely destroying the current client
     * and constructing a new one using the provided {@link MessagingServiceConfig}.
     * This operation resubscribes to all registered channels.
     *
     * @param config a new config to use
     * @return {@code true} if the reload is successful, {@code false} otherwise
     */
    public boolean reload(MessagingServiceConfig config) {
        this.config = config;
        return reload();
    }

    /**
     * Completely shutdowns the service and destroys all
     * related thread pools.
     */
    public void shutdown() {
        eventBus.shutdown();
        redisClient.shutdown();
        timeoutsScheduler.shutdownNow();
    }

    /**
     * Gets RxPubSub {@link EventBus} used for listening
     * to the reception of pubsub messages.
     * @return the event bus
     */
    public EventBus getEventBus() {
        return eventBus;
    }

    /**
     * Registers {@link PubSubMessage} to handle sending and receiving it.
     *
     * @param clazz pubsub message class to register
     * @return {@link MessagingService} instance for chaining purposes
     */
    public MessagingService registerMessageClass(Class<? extends PubSubMessage> clazz) {
        final var classId = AnnotationUtils.getPubSubClassId(clazz);
        final var registeredClass = registeredMessages.get(classId);

        if (registeredClass == null) {
            registeredMessages.put(classId, clazz);
            return this;
        }

        if (clazz == registeredClass) {
            logger.warn("Message class " + clazz.getSimpleName() + " already registered!");
            return this;
        }

        logger.warn("Unable to register message class " + clazz.getSimpleName() + ". Message ID "
                + classId + " is already taken by " + registeredClass.getSimpleName() + " class!");
        return this;
    }

    /**
     * Registers {@link PubSubMessage}s to handle sending and receiving it.
     *
     * @param classes pubsub messages classes to register
     * @return {@link MessagingService} instance for chaining purposes
     */
    @SafeVarargs
    public final MessagingService registerMessageClass(Class<? extends PubSubMessage>... classes) {
        for (var clazz : classes) registerMessageClass(clazz);
        return this;
    }

    /**
     * Registers {@link PubSubChannel} as incoming channel to receive messages from it.
     *
     * @param channel pubsub channel to register
     * @return {@link MessagingService} instance for chaining purposes
     */
    public MessagingService registerIncomingChannel(PubSubChannel channel) {
        Objects.requireNonNull(channel , "channel is null");
        if (registeredIncomingChannels.get(channel.getId()) != null) {
            logger.warn("Channel " + channel.getId() + " already registered as incoming channel!");
            return this;
        }
        registeredIncomingChannels.put(channel.getId(), channel);
        subscribe(channel);
        return this;
    }

    /**
     * Registers {@link PubSubChannel} as outgoing channel to publish messages in it.
     *
     * @param channel pubsub channel to register
     * @return {@link MessagingService} instance for chaining purposes
     */
    public MessagingService registerOutgoingChannel(PubSubChannel channel) {
        Objects.requireNonNull(channel , "channel is null");
        if (registeredOutgoingChannels.get(channel.getId()) != null) {
            logger.warn("Channel " + channel.getId() + " already registered as outgoing channel!");
            return this;
        }
        registeredOutgoingChannels.put(channel.getId(), channel);
        return this;
    }

    /**
     * Registers {@link PubSubChannel} as duplex (incoming & outgoing) channel
     * to receive and publish messages with it.
     *
     * @param channel pubsub channel to register
     * @return {@link MessagingService} instance for chaining purposes
     */
    public MessagingService registerDuplexChannel(PubSubChannel channel) {
        return registerIncomingChannel(channel).registerOutgoingChannel(channel);
    }

    /**
     * Publishes {@link PubSubMessage} in {@link PubSubChannel}.
     * @param channel channel to publish the message to
     * @param message message to publish
     * @param error returns error state
     */
    public void publishMessage(PubSubChannel channel, PubSubMessage message, Consumer<Boolean> error) {
        if (!registeredOutgoingChannels.containsKey(channel.getId())) {
            if (error != null) {
                error.accept(true);
            }
            throw new IllegalArgumentException("Cannot send message" + message + " in channel " + channel.getId()
                    + " since it isn't registered as outgoing channel!");
        }

        ForkJoinPool.commonPool().execute(() -> {
            var classId = registeredMessages.entrySet().stream()
                    .filter(e -> e.getValue() == message.getClass())
                    .findAny();

            if (classId.isEmpty()) {
                if (error != null) {
                    error.accept(true);
                }
                throw new IllegalArgumentException("Cannot send message " + message + " since provided message class "
                        + message.getClass() + " isn't registered!");
            }

            JsonObject jsonMessage = setMessageClass(
                    GSON.toJsonTree(message).getAsJsonObject(),
                    classId.get().getKey()
            );

            pubConnection.async()
                    .publish(channel.getId(), GSON.toJson(jsonMessage))
                    .whenComplete((reached, ex) -> {
                        if (ex != null) {
                            logger.error("Failed to publish message " + message, ex);
                            if (error != null) {
                                error.accept(true);
                            }
                            return;
                        }
                        if (error != null) {
                            error.accept(false);
                        }
                    });
        });
    }

    /**
     * Publishes {@link PubSubMessage} in {@link PubSubChannel}.
     *
     * @param channel channel to publish the message to
     * @param message message to publish
     */
    public void publishMessage(PubSubChannel channel, PubSubMessage message) {
        publishMessage(channel, message, (Consumer<Boolean>) null);
    }

    /**
     * Publishes {@link PubSubMessage} in {@link PubSubChannel} and waits for callback.
     *
     * @param channel channel to publish the message to
     * @param message message to publish
     * @param handler callback message handler
     */
    public <T extends PubSubMessage> void publishMessage(
            PubSubChannel channel, PubSubMessage message, CallbackHandler<T> handler
    ) {
        var wrapper = new CallbackHandlerWrapper<>(handler, timeoutsScheduler);
        awaitingCallbacks.put(message.getMessageId(), wrapper);
        publishMessage(channel, message, error -> {
            if (!error) return;
            awaitingCallbacks.remove(message.getMessageId());
            wrapper.exceptionally(new ConnectException());
        });
    }
}
