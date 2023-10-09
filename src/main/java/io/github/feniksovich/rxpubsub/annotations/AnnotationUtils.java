package io.github.feniksovich.rxpubsub.annotations;

import io.github.feniksovich.rxpubsub.model.PubSubMessage;

public class AnnotationUtils {

    /**
     * Retrieves or generates a class id that represents the pubsub message.
     * @param clazz target pubsub message class
     * @return Custom class id if {@link OverridePubSubClassId} annotation presents
     * and its value is not blank, simple class name otherwise
     * @see String#isBlank()
     * @see Class#getSimpleName()
     */
    public static String getPubSubClassId(Class<? extends PubSubMessage> clazz) {
        var annotation = clazz.getAnnotation(OverridePubSubClassId.class);
        if (annotation != null && !annotation.value().isBlank()) {
            return annotation.value();
        }
        return clazz.getSimpleName();
    }
}
