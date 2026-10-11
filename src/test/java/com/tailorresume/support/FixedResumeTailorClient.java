package com.tailorresume.support;

import com.tailorresume.tailor.ResumeData;
import com.tailorresume.tailor.ResumeTailorClient;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Returns a canned resume instead of calling Claude.
 *
 * <p>Lets the pipeline tests exercise quota enforcement, persistence and
 * rendering without a key, a network call or a bill. The call counter is what
 * proves the quota guard runs <em>before</em> the model, not after.
 */
public class FixedResumeTailorClient implements ResumeTailorClient {

    private final AtomicInteger calls = new AtomicInteger();

    @Override
    public String modelName() {
        return "fixed-test-client";
    }

    @Override
    public ResumeData tailor(String resumeText, String jobDescription) {
        calls.incrementAndGet();
        return SampleResumes.complete();
    }

    public int callCount() {
        return calls.get();
    }
}
