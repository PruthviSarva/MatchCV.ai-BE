package com.tailorresume.tailor;

import com.tailorresume.common.ApiException;

/**
 * Stands in for Claude when no API key is configured. Wired in by
 * {@link com.tailorresume.config.ClaudeConfig}.
 *
 * <p>It refuses loudly rather than returning a plausible fake resume: a
 * silently stubbed tailoring would look like a working product and ship a
 * useless document to a real job application. Tests that exercise the pipeline
 * supply their own client instead.
 */
public class StubResumeTailorClient implements ResumeTailorClient {

    @Override
    public String modelName() {
        return "stub";
    }

    @Override
    public ResumeData tailor(String resumeText, String jobDescription) {
        throw ApiException.upstreamFailure(
                "Resume tailoring is not configured on this server. "
                        + "Set CLAUDE_API_KEY and restart.");
    }
}
