package com.onlineinterview.notification.chat;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ChatMessageFormatterTest {
    private final ChatMessageFormatter formatter = new ChatMessageFormatter();

    private ChatMessage sample(boolean positive) {
        return new ChatMessage(
                "Interview result finalized",
                positive ? "Alice was selected." : "Alice was not selected.",
                positive,
                List.of(new ChatMessage.Field("Candidate", "Alice"),
                        new ChatMessage.Field("Outcome", positive ? "PASSED" : "NOT SELECTED")),
                "Open review", "https://example.test/review/1");
    }

    @Test
    @SuppressWarnings("unchecked")
    void slackPayloadHasColouredAttachmentWithFields() {
        var body = formatter.format(ChatProvider.SLACK, sample(true));
        assertThat(body).containsEntry("text", "Interview result finalized");
        var attachments = (List<Map<String, Object>>) body.get("attachments");
        assertThat(attachments).hasSize(1);
        var attachment = attachments.get(0);
        assertThat(attachment).containsEntry("color", "#2eb67d")
                .containsEntry("title_link", "https://example.test/review/1");
        assertThat((List<?>) attachment.get("fields")).hasSize(2);
    }

    @Test
    @SuppressWarnings("unchecked")
    void slackUsesRedColourWhenNotPositive() {
        var body = formatter.format(ChatProvider.SLACK, sample(false));
        var attachment = ((List<Map<String, Object>>) body.get("attachments")).get(0);
        assertThat(attachment).containsEntry("color", "#e01e5a");
    }

    @Test
    @SuppressWarnings("unchecked")
    void teamsPayloadIsMessageCardWithFactsAndAction() {
        var body = formatter.format(ChatProvider.MICROSOFT_TEAMS, sample(true));
        assertThat(body).containsEntry("@type", "MessageCard")
                .containsEntry("themeColor", "2eb67d")
                .containsEntry("title", "Interview result finalized");
        var sections = (List<Map<String, Object>>) body.get("sections");
        assertThat((List<?>) sections.get(0).get("facts")).hasSize(2);
        assertThat(body).containsKey("potentialAction");
    }

    @Test
    @SuppressWarnings("unchecked")
    void discordPayloadHasEmbedWithNumericColour() {
        var body = formatter.format(ChatProvider.DISCORD, sample(true));
        assertThat(body).containsEntry("content", "Interview result finalized");
        var embed = ((List<Map<String, Object>>) body.get("embeds")).get(0);
        assertThat(embed).containsEntry("color", 0x2eb67d)
                .containsEntry("url", "https://example.test/review/1");
        assertThat((List<?>) embed.get("fields")).hasSize(2);
    }

    @Test
    void googleChatPayloadIsMarkdownTextWithFieldsAndLink() {
        var body = formatter.format(ChatProvider.GOOGLE_CHAT, sample(true));
        var text = (String) body.get("text");
        assertThat(text).contains("*Interview result finalized*")
                .contains("*Candidate:* Alice")
                .contains("<https://example.test/review/1|Open review>");
    }

    @Test
    @SuppressWarnings("unchecked")
    void genericPayloadIsNeutralStructure() {
        var body = formatter.format(ChatProvider.WEBHOOK, sample(false));
        assertThat(body).containsEntry("title", "Interview result finalized")
                .containsEntry("status", "negative")
                .containsEntry("url", "https://example.test/review/1");
        assertThat((Map<String, Object>) body.get("fields")).containsEntry("Candidate", "Alice");
    }

    @Test
    @SuppressWarnings("unchecked")
    void omitsLinkWhenNotProvided() {
        var message = new ChatMessage("Title", "Summary", true, List.of(), null, null);
        var slack = ((List<Map<String, Object>>) formatter.format(ChatProvider.SLACK, message)
                .get("attachments")).get(0);
        assertThat(slack).doesNotContainKey("title_link");
        assertThat(formatter.format(ChatProvider.MICROSOFT_TEAMS, message))
                .doesNotContainKey("potentialAction");
        assertThat(formatter.format(ChatProvider.WEBHOOK, message)).doesNotContainKey("url");
    }
}
