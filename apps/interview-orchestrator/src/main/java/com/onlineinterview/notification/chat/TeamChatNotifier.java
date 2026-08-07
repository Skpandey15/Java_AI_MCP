package com.onlineinterview.notification.chat;

import java.net.URI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/** Posts interview events to every configured team chat provider (Slack, Microsoft Teams,
 *  Discord, Google Chat, and a generic webhook) via their incoming-webhook URLs.
 *
 *  <p>Best-effort by design: a provider being unconfigured is skipped, and a delivery
 *  failure is logged but never propagated, so chat problems can never fail the interview
 *  flow that triggered the notification. */
@Component
public class TeamChatNotifier {
    private static final Logger log = LoggerFactory.getLogger(TeamChatNotifier.class);

    private final RestClient client;
    private final ChatNotificationProperties properties;
    private final ChatMessageFormatter formatter;

    @Autowired
    public TeamChatNotifier(RestClient.Builder builder, ChatNotificationProperties properties,
            ChatMessageFormatter formatter) {
        this(builder.build(), properties, formatter);
    }

    TeamChatNotifier(RestClient client, ChatNotificationProperties properties,
            ChatMessageFormatter formatter) {
        this.client = client;
        this.properties = properties;
        this.formatter = formatter;
    }

    /** Delivers the message to each provider that has a webhook URL configured. Never throws. */
    public void notify(ChatMessage message) {
        if (!properties.isEnabled()) {
            return;
        }
        post(ChatProvider.SLACK, properties.getSlackWebhookUrl(), message);
        post(ChatProvider.MICROSOFT_TEAMS, properties.getTeamsWebhookUrl(), message);
        post(ChatProvider.DISCORD, properties.getDiscordWebhookUrl(), message);
        post(ChatProvider.GOOGLE_CHAT, properties.getGoogleChatWebhookUrl(), message);
        post(ChatProvider.WEBHOOK, properties.getWebhookUrl(), message);
    }

    private void post(ChatProvider provider, String url, ChatMessage message) {
        if (url == null || url.isBlank()) {
            return;
        }
        try {
            client.post()
                    .uri(URI.create(url))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(formatter.format(provider, message))
                    .retrieve()
                    .toBodilessEntity();
            log.atInfo().addKeyValue("event", "chat.notified")
                    .addKeyValue("provider", provider)
                    .log("Team chat notification delivered");
        } catch (RestClientException | IllegalArgumentException exception) {
            log.atWarn().addKeyValue("event", "chat.notify_failed")
                    .addKeyValue("provider", provider)
                    .setCause(exception)
                    .log("Team chat notification failed (ignored)");
        }
    }
}
