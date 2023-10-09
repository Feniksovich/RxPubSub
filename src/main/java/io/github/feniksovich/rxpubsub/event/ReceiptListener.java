package io.github.feniksovich.rxpubsub.event;

import io.github.feniksovich.rxpubsub.model.PubSubMessage;

@FunctionalInterface
interface ReceiptListener {
    void execute(PubSubMessage message);
}
