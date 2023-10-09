package io.github.feniksovich.rxpubsub.event;

import io.github.feniksovich.rxpubsub.MessagingService;
import io.github.feniksovich.rxpubsub.model.PubSubChannel;
import io.github.feniksovich.rxpubsub.model.PubSubMessage;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.function.Function;

public class EventBusImpl implements EventBus {

    private final MessagingService messagingService;
    private final ConcurrentHashMap<Class<? extends PubSubMessage>, List<ReceiptListener>> handlers = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Class<? extends PubSubMessage>, List<ReceiptListener>> respondingHandlers = new ConcurrentHashMap<>();
    private final ExecutorService listenersExecutor = Executors.newCachedThreadPool();

    public EventBusImpl(MessagingService messagingService) {
        this.messagingService = messagingService;
    }

    public <T extends PubSubMessage> EventBus registerReceiptListener(Class<T> messageClass, Consumer<T> handler) {
        // Create new listener object
        final var listener = new ReceiptListener() {
            @Override
            public void execute(PubSubMessage message) {
                handler.accept(messageClass.cast(message));
            }
        };
        registerListener(messageClass, listener, handlers);
        return this;
    }

    public <T extends PubSubMessage> EventBus registerRespondingListener(
            Class<T> messageClass, PubSubChannel channel, Function<T, PubSubMessage> handler
    ) {
        // Create new listener object
        final var listener = new ReceiptListener() {
            @Override
            public void execute(PubSubMessage message) {
                var callbackMessage = handler.apply(messageClass.cast(message));
                callbackMessage.setResponseTo(message.getMessageId());
                messagingService.publishMessage(channel, callbackMessage);
            }
        };
        registerListener(messageClass, listener, respondingHandlers);
        return this;
    }

    private <T extends PubSubMessage> void registerListener(
            Class<T> messageClass, ReceiptListener listener,
            ConcurrentHashMap<Class<? extends PubSubMessage>, List<ReceiptListener>> list
    ) {
        final var listeners = list.get(messageClass);
        if (listeners == null) {
            respondingHandlers.put(messageClass, new ArrayList<>(Collections.singletonList(listener)));
            return;
        }
        listeners.add(listener);
    }

    public <T extends PubSubMessage> void handleMessage(Class<T> messageClass, PubSubMessage message) {
        final var listeners = handlers.get(messageClass);
        final var respondingListeners = respondingHandlers.get(messageClass);
        if (listeners != null) listeners
                .forEach(listener -> listenersExecutor.execute(() -> listener.execute(message)));
        if (respondingListeners != null) respondingListeners
                .forEach(listener -> listenersExecutor.execute(() -> listener.execute(message)));
    }

    public void shutdown() {
        listenersExecutor.shutdown();
    }
}
