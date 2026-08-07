package com.onlineinterview.notification.chat;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class TeamChatNotifierTest {
    private static final String SLACK = "https://hooks.slack.test/services/T/B/x";
    private static final String DISCORD = "https://discord.test/api/webhooks/1/y";

    private final ChatMessageFormatter formatter = new ChatMessageFormatter();

    private ChatMessage message() {
        return new ChatMessage("Interview result finalized", "Alice was selected.", true,
                List.of(new ChatMessage.Field("Candidate", "Alice")), null, null);
    }

    private ChatNotificationProperties properties(boolean enabled) {
        var properties = new ChatNotificationProperties();
        properties.setEnabled(enabled);
        return properties;
    }

    @Test
    void postsToEachConfiguredProviderInOrder() {
        var builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        var properties = properties(true);
        properties.setSlackWebhookUrl(SLACK);
        properties.setDiscordWebhookUrl(DISCORD);
        server.expect(requestTo(SLACK)).andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("ok", MediaType.APPLICATION_JSON));
        server.expect(requestTo(DISCORD)).andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        new TeamChatNotifier(builder.build(), properties, formatter).notify(message());

        server.verify();
    }

    @Test
    void sendsNothingWhenDisabled() {
        var builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        var properties = properties(false);
        properties.setSlackWebhookUrl(SLACK);

        new TeamChatNotifier(builder.build(), properties, formatter).notify(message());

        server.verify(); // no requests expected or made
    }

    @Test
    void skipsProvidersWithoutAWebhookUrl() {
        var builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        var properties = properties(true); // enabled, but no URLs set

        new TeamChatNotifier(builder.build(), properties, formatter).notify(message());

        server.verify();
    }

    @Test
    void swallowsDeliveryFailures() {
        var builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        var properties = properties(true);
        properties.setSlackWebhookUrl(SLACK);
        server.expect(requestTo(SLACK)).andRespond(withServerError());

        var notifier = new TeamChatNotifier(builder.build(), properties, formatter);

        assertThatCode(() -> notifier.notify(message())).doesNotThrowAnyException();
        server.verify();
    }
}
