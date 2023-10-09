package io.github.feniksovich.rxpubsub.annotations;

import java.lang.annotation.*;

/**
 * Allows overriding the ID of the class that represents the pubsub message.
 * @since 1.0.0
 */
@Documented
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface OverridePubSubClassId {
    String value();
}
