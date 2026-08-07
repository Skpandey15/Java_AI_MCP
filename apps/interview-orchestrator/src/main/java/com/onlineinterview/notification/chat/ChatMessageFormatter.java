package com.onlineinterview.notification.chat;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/** Renders a provider-agnostic {@link ChatMessage} into each provider's incoming-webhook
 *  JSON body. Returned maps are serialized to JSON by the HTTP client. Pure and stateless. */
@Component
public class ChatMessageFormatter {

    private static final String SLACK_GREEN = "#2eb67d";
    private static final String SLACK_RED = "#e01e5a";
    private static final String TEAMS_GREEN = "2eb67d";
    private static final String TEAMS_RED = "e01e5a";
    private static final int DISCORD_GREEN = 0x2eb67d;
    private static final int DISCORD_RED = 0xe01e5a;

    public Map<String, Object> format(ChatProvider provider, ChatMessage message) {
        return switch (provider) {
            case SLACK -> slack(message);
            case MICROSOFT_TEAMS -> teams(message);
            case DISCORD -> discord(message);
            case GOOGLE_CHAT -> googleChat(message);
            case WEBHOOK -> generic(message);
        };
    }

    // Slack incoming webhook: a fallback text plus a coloured attachment with fields.
    private Map<String, Object> slack(ChatMessage message) {
        var attachment = new LinkedHashMap<String, Object>();
        attachment.put("color", message.positive() ? SLACK_GREEN : SLACK_RED);
        attachment.put("title", message.title());
        if (hasLink(message)) {
            attachment.put("title_link", message.linkUrl());
        }
        attachment.put("text", message.summary());
        var slackFields = new ArrayList<Map<String, Object>>();
        for (ChatMessage.Field field : message.fields()) {
            var entry = new LinkedHashMap<String, Object>();
            entry.put("title", field.name());
            entry.put("value", field.value());
            entry.put("short", true);
            slackFields.add(entry);
        }
        attachment.put("fields", slackFields);
        var body = new LinkedHashMap<String, Object>();
        body.put("text", message.title());
        body.put("attachments", List.of(attachment));
        return body;
    }

    // Microsoft Teams legacy MessageCard (Office 365 connector / Workflows compatible).
    private Map<String, Object> teams(ChatMessage message) {
        var facts = new ArrayList<Map<String, Object>>();
        for (ChatMessage.Field field : message.fields()) {
            var fact = new LinkedHashMap<String, Object>();
            fact.put("name", field.name());
            fact.put("value", field.value());
            facts.add(fact);
        }
        var section = new LinkedHashMap<String, Object>();
        section.put("text", message.summary());
        section.put("facts", facts);
        var body = new LinkedHashMap<String, Object>();
        body.put("@type", "MessageCard");
        body.put("@context", "https://schema.org/extensions");
        body.put("themeColor", message.positive() ? TEAMS_GREEN : TEAMS_RED);
        body.put("summary", message.title());
        body.put("title", message.title());
        body.put("sections", List.of(section));
        if (hasLink(message)) {
            var action = new LinkedHashMap<String, Object>();
            action.put("@type", "OpenUri");
            action.put("name", linkText(message));
            action.put("targets", List.of(Map.of("os", "default", "uri", message.linkUrl())));
            body.put("potentialAction", List.of(action));
        }
        return body;
    }

    // Discord webhook: a single rich embed.
    private Map<String, Object> discord(ChatMessage message) {
        var embedFields = new ArrayList<Map<String, Object>>();
        for (ChatMessage.Field field : message.fields()) {
            var entry = new LinkedHashMap<String, Object>();
            entry.put("name", field.name());
            entry.put("value", field.value());
            entry.put("inline", true);
            embedFields.add(entry);
        }
        var embed = new LinkedHashMap<String, Object>();
        embed.put("title", message.title());
        embed.put("description", message.summary());
        embed.put("color", message.positive() ? DISCORD_GREEN : DISCORD_RED);
        embed.put("fields", embedFields);
        if (hasLink(message)) {
            embed.put("url", message.linkUrl());
        }
        var body = new LinkedHashMap<String, Object>();
        body.put("content", message.title());
        body.put("embeds", List.of(embed));
        return body;
    }

    // Google Chat: simple text with lightweight markdown.
    private Map<String, Object> googleChat(ChatMessage message) {
        var text = new StringBuilder();
        text.append('*').append(message.title()).append("*\n").append(message.summary());
        for (ChatMessage.Field field : message.fields()) {
            text.append("\n*").append(field.name()).append(":* ").append(field.value());
        }
        if (hasLink(message)) {
            text.append('\n').append('<').append(message.linkUrl())
                    .append('|').append(linkText(message)).append('>');
        }
        var body = new LinkedHashMap<String, Object>();
        body.put("text", text.toString());
        return body;
    }

    // Generic webhook: a neutral, structured payload for any custom consumer.
    private Map<String, Object> generic(ChatMessage message) {
        var fieldMap = new LinkedHashMap<String, Object>();
        for (ChatMessage.Field field : message.fields()) {
            fieldMap.put(field.name(), field.value());
        }
        var body = new LinkedHashMap<String, Object>();
        body.put("title", message.title());
        body.put("summary", message.summary());
        body.put("status", message.positive() ? "positive" : "negative");
        body.put("fields", fieldMap);
        if (hasLink(message)) {
            body.put("url", message.linkUrl());
        }
        return body;
    }

    private boolean hasLink(ChatMessage message) {
        return message.linkUrl() != null && !message.linkUrl().isBlank();
    }

    private String linkText(ChatMessage message) {
        return message.linkText() == null || message.linkText().isBlank()
                ? "Open" : message.linkText();
    }
}
