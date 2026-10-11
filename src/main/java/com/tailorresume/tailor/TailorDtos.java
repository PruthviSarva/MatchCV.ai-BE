package com.tailorresume.tailor;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public final class TailorDtos {

    private TailorDtos() {
    }

    /**
     * Either reference a saved resume by id, or paste text inline.
     *
     * <p>Inline text exists for the first-time flow: the frontend can upload
     * and tailor in one step without making the user save a resume first.
     */
    public record TailorRequest(
            Long baseResumeId,

            @Size(max = 60_000, message = "That resume text is too long")
            String resumeText,

            @NotBlank(message = "Paste the job description")
            @Size(min = 50, message = "That job description is too short to tailor against")
            @Size(max = 40_000, message = "That job description is too long")
            String jobDescription) {
    }

    /** History list: metadata only, so the list view is cheap. */
    public record TailoredResumeSummary(
            Long id,
            String jobTitle,
            int matchScore,
            Long baseResumeId,
            Instant createdAt) {

        public static TailoredResumeSummary from(TailoredResume tailored) {
            return new TailoredResumeSummary(
                    tailored.getId(),
                    tailored.getJobTitle(),
                    tailored.getMatchScore(),
                    tailored.getBaseResume() == null ? null : tailored.getBaseResume().getId(),
                    tailored.getCreatedAt());
        }
    }

    /** The full result: the structured resume the frontend renders, plus context. */
    public record TailoredResumeDetail(
            Long id,
            String jobTitle,
            int matchScore,
            String modelName,
            Instant createdAt,
            ResumeData resume) {
    }
}
