package com.tailorresume.config;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.tailorresume.tailor.ClaudeResumeTailorClient;
import com.tailorresume.tailor.ResumeTailorClient;
import com.tailorresume.tailor.StubResumeTailorClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * Wires the rewriting client.
 *
 * <p>With no API key the stub is used, so the app boots and the whole test
 * suite runs without a key and without network access. The swap happens here
 * rather than inside the client so nothing downstream has to know which one it
 * is holding.
 */
@Configuration
public class ClaudeConfig {

    private static final Logger log = LoggerFactory.getLogger(ClaudeConfig.class);

    @Bean
    public ResumeTailorClient resumeTailorClient(AppProperties properties) {
        AppProperties.Claude config = properties.claude();

        if (!config.isEnabled()) {
            log.warn("CLAUDE_API_KEY is not set — resume tailoring will refuse requests.");
            return new StubResumeTailorClient();
        }

        AnthropicClient client = AnthropicOkHttpClient.builder()
                .apiKey(config.apiKey())
                // A tailoring is one long request, not a chat turn: the model
                // thinks, then writes a whole resume. The SDK default would be
                // generous enough, but pinning it makes the budget explicit.
                .timeout(Duration.ofMinutes(5))
                .build();

        log.info("Resume tailoring enabled on model {}", config.model());
        return new ClaudeResumeTailorClient(client, config);
    }
}
