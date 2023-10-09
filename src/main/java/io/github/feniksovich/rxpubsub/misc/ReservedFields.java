package io.github.feniksovich.rxpubsub.misc;

/**
 * Utility class that stores reserved fields constants.
 */
public class ReservedFields {

    private ReservedFields() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }

    public static final String ROOT_NAMESPACE = "@rxps";
    public static final String MESSAGE_CLASS_IDENTIFIER_FIELD = ROOT_NAMESPACE + "_class_id";
    public static final String MESSAGE_ID_FIELD = ROOT_NAMESPACE + "_message_id";
    public static final String MESSAGE_RESPONSE_TO_ID_FIELD = ROOT_NAMESPACE + "_response_to";
}
