package com.onlineinterview.notification.chat;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** Team chat notification settings. Disabled by default; each provider is enabled simply
 *  by supplying its incoming-webhook URL. Values come from environment variables (see
 *  application.yml {@code app.chat}). */
@Component
@ConfigurationProperties(prefix = "app.chat")
public class ChatNotificationProperties {
    private boolean enabled;
    private String slackWebhookUrl = "";
    private String teamsWebhookUrl = "";
    private String discordWebhookUrl = "";
    private String googleChatWebhookUrl = "";
    private String webhookUrl = "";

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean value) { enabled = value; }

    public String getSlackWebhookUrl() { return slackWebhookUrl; }
    public void setSlackWebhookUrl(String value) { slackWebhookUrl = value; }

    public String getTeamsWebhookUrl() { return teamsWebhookUrl; }
    public void setTeamsWebhookUrl(String value) { teamsWebhookUrl = value; }

    public String getDiscordWebhookUrl() { return discordWebhookUrl; }
    public void setDiscordWebhookUrl(String value) { discordWebhookUrl = value; }

    public String getGoogleChatWebhookUrl() { return googleChatWebhookUrl; }
    public void setGoogleChatWebhookUrl(String value) { googleChatWebhookUrl = value; }

    public String getWebhookUrl() { return webhookUrl; }
    public void setWebhookUrl(String value) { webhookUrl = value; }
}
