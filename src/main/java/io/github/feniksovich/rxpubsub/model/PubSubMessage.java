package io.github.feniksovich.rxpubsub.model;

import com.google.gson.annotations.SerializedName;
import io.github.feniksovich.rxpubsub.misc.ReservedFields;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * The base class for all pub/sub messages.
 */
public abstract class PubSubMessage {

    @SerializedName(ReservedFields.MESSAGE_ID_FIELD)
    private String messageId = UUID.randomUUID().toString();

    @SerializedName(ReservedFields.MESSAGE_RESPONSE_TO_ID_FIELD)
    protected String responseTo;

    public String getMessageId() {
        return messageId;
    }

    /**
     * Sets a custom message ID of pre-generated one instead.
     * @param messageId ID to set
     */
    public void setMessageId(String messageId) {
        this.messageId = messageId;
    }

    /**
     * Returns the source message ID that this message responds to.
     * @return source message ID if present
     */
    public Optional<String> getResponseTo() {
        return Optional.ofNullable(responseTo);
    }

    /**
     * Sets the source message ID that this message responds to.
     * @param responseTo source message ID
     */
    public void setResponseTo(String responseTo) {
        this.responseTo = responseTo;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PubSubMessage that = (PubSubMessage) o;
        return Objects.equals(messageId, that.messageId) && Objects.equals(responseTo, that.responseTo);
    }

    @Override
    public int hashCode() {
        return Objects.hash(messageId, responseTo);
    }
}
