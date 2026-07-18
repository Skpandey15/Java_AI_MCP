package com.onlineinterview.ai;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.web.client.RestClient;

class AiQuestionClientWiringTest {
    @Test
    void springCreatesClientWhenTestConstructorAlsoExists() {
        try (var context = new AnnotationConfigApplicationContext()) {
            context.getEnvironment().getPropertySources().addFirst(new MapPropertySource(
                    "test", Map.of(
                            "app.ai-service.base-url", "http://127.0.0.1:8000",
                            "app.ai-service.service-token", "service-token")));
            context.registerBean(RestClient.Builder.class, RestClient::builder);
            context.registerBean(ObjectMapper.class, ObjectMapper::new);
            context.register(AiQuestionClient.class);

            context.refresh();

            assertThat(context.getBean(AiQuestionClient.class)).isNotNull();
        }
    }
}
