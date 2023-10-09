package io.github.feniksovich.rxpubsub.model;

import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public final class CallbackHandlerWrapper<T extends PubSubMessage> extends CallbackHandler<T> {

    private final ScheduledFuture<?> scheduledTimeoutTask;

    public CallbackHandlerWrapper(
            CallbackHandler<T> callbackHandler,
            ScheduledThreadPoolExecutor scheduler
    ) {
        super(callbackHandler);
        this.scheduledTimeoutTask = scheduler.schedule(this::timeout, timeoutThreshold, TimeUnit.SECONDS);
    }

    public void accept(Class<? extends PubSubMessage> detectedClass, PubSubMessage message) {
        if (detectedClass != callbackMessageClass) return;
        scheduledTimeoutTask.cancel(true);
        handler.accept(callbackMessageClass.cast(message));
    }

    public void exceptionally(Throwable throwable) {
        scheduledTimeoutTask.cancel(true);
        if (errorHandler != null) {
            errorHandler.accept(throwable);
        }
        if (failureHandler != null) {
            failureHandler.run();
        }
    }

    public void timeout() {
        scheduledTimeoutTask.cancel(true);
        if (timeoutHandler != null) {
            timeoutHandler.run();
        }
        if (failureHandler != null) {
            failureHandler.run();
        }
    }
}
