package com.tailorresume.tailor;

/**
 * Rewrites a resume against a job description.
 *
 * <p>An interface with two implementations: the real Claude client, and a stub
 * that runs with no key and no network so the app boots and the test suite
 * passes without calling a paid API.
 */
public interface ResumeTailorClient {

    /** Identifies which implementation ran; stored on the tailoring row. */
    String modelName();

    /**
     * @throws com.tailorresume.common.ApiException 502 when the provider fails
     *         or returns something unusable
     */
    ResumeData tailor(String resumeText, String jobDescription);
}
