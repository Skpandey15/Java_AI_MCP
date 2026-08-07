package com.onlineinterview.notification.chat;

import java.util.List;

/** Provider-agnostic chat message. {@link ChatMessageFormatter} renders it into each
 *  provider's payload shape, so callers describe the message once. */
public record ChatMessage(
        String title,
        String summary,
        boolean positive,
        List<Field> fields,
        String linkText,
        String linkUrl) {

    public ChatMessage {
        fields = fields == null ? List.of() : List.copyOf(fields);
    }

    /** A labelled key/value shown as a compact fact in the message. */
    public record Field(String name, String value) {}
}
