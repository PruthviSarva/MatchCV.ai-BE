package com.tailorresume.tailor;

import com.anthropic.client.AnthropicClient;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.StructuredMessageCreateParams;
import com.tailorresume.common.ApiException;
import com.tailorresume.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Rewrites the resume with Claude, using structured outputs.
 *
 * <p>The schema is derived from the {@link ResumeData} record, so the model is
 * constrained to emit exactly that object and the SDK hands it back typed.
 * That removes the whole failure class the first version of this app had:
 * asking for JSON in the prompt, then stripping code fences, hunting for the
 * first {@code &#123;}, and parsing by hand.
 *
 * <p>Thinking is left at the model default (adaptive, on) rather than disabled.
 * Deciding which of a candidate's bullets actually support a posting's
 * requirements is exactly the kind of judgement that benefits from it, and
 * disabling thinking on this model has its own failure modes. The token ceiling
 * is sized to cover thinking plus the resume JSON.
 */
public class ClaudeResumeTailorClient implements ResumeTailorClient {

    private static final Logger log = LoggerFactory.getLogger(ClaudeResumeTailorClient.class);

    private final AnthropicClient client;
    private final AppProperties.Claude config;

    public ClaudeResumeTailorClient(AnthropicClient client, AppProperties.Claude config) {
        this.client = client;
        this.config = config;
    }

    @Override
    public String modelName() {
        return config.model();
    }

    @Override
    public ResumeData tailor(String resumeText, String jobDescription) {
        StructuredMessageCreateParams<ResumeData> params = MessageCreateParams.builder()
                .model(config.model())
                .maxTokens(config.maxTokens().longValue())
                .system(TailorPrompt.SYSTEM)
                .outputConfig(ResumeData.class)
                .addUserMessage(TailorPrompt.user(resumeText, jobDescription))
                .build();

        try {
            var response = client.messages().create(params);

            // A safety classifier can decline a request and still return 200,
            // with no content at all. Reading content[0] first would surface
            // that as a confusing parse failure instead of what it is.
            if ("refusal".equals(String.valueOf(response.stopReason().orElse(null)))) {
                log.warn("Claude declined the tailoring request");
                throw ApiException.upstreamFailure(
                        "The model declined to process this job description. "
                                + "Check the pasted text and try again.");
            }

            return response.content().stream()
                    .flatMap(block -> block.text().stream())
                    .map(typed -> typed.text())
                    .findFirst()
                    .map(ResumeDataNormalizer::normalize)
                    .orElseThrow(() -> {
                        // Almost always the token ceiling: the response is all
                        // thinking and the JSON never arrives.
                        log.warn("Claude returned no structured content (stop reason {})",
                                response.stopReason().orElse(null));
                        return ApiException.upstreamFailure(
                                "The model did not return a usable resume. Try again.");
                    });

        } catch (ApiException alreadyMapped) {
            throw alreadyMapped;
        } catch (RuntimeException failure) {
            // Provider detail (status codes, request ids, prompt echoes) is
            // logged, never returned.
            log.error("Claude request failed", failure);
            throw ApiException.upstreamFailure(
                    "The tailoring service is unavailable right now. Try again in a moment.");
        }
    }
}
