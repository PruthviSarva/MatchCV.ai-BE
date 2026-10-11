package com.tailorresume.resume;

import java.time.Instant;

public final class ResumeDtos {

    private ResumeDtos() {
    }

    /** List view: metadata only, so the account screen does not ship resume text. */
    public record BaseResumeSummary(
            Long id,
            String label,
            String sourceFilename,
            String sourceType,
            int characterCount,
            Instant createdAt) {

        public static BaseResumeSummary from(BaseResume resume) {
            return new BaseResumeSummary(
                    resume.getId(),
                    resume.getLabel(),
                    resume.getSourceFilename(),
                    resume.getSourceType(),
                    resume.getCharacterCount(),
                    resume.getCreatedAt());
        }
    }

    /** Detail view, including the extracted text so the user can check what we read. */
    public record BaseResumeDetail(
            Long id,
            String label,
            String sourceFilename,
            String sourceType,
            int characterCount,
            Instant createdAt,
            String extractedText) {

        public static BaseResumeDetail from(BaseResume resume) {
            return new BaseResumeDetail(
                    resume.getId(),
                    resume.getLabel(),
                    resume.getSourceFilename(),
                    resume.getSourceType(),
                    resume.getCharacterCount(),
                    resume.getCreatedAt(),
                    resume.getExtractedText());
        }
    }
}
