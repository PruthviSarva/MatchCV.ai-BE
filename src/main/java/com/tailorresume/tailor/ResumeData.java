package com.tailorresume.tailor;

import java.util.List;

/**
 * The tailored resume, as structured data.
 *
 * <p>This record is the contract in three places at once: it is the JSON schema
 * the model is constrained to (the Anthropic SDK derives it from these
 * components), the shape persisted on the tailoring row, and the payload the
 * frontend renders. Keeping it as one type is what stops the three drifting.
 *
 * <p>Every component is non-optional on purpose. A strict schema with no
 * optional fields means the model must emit each key, so the renderer never has
 * to distinguish "absent" from "empty" — an empty list or an empty string is
 * always the answer for "nothing here".
 */
public record ResumeData(
        String name,
        String email,
        String phone,
        /** City and country. Street addresses are a liability and ATS ignores them. */
        String location,
        String linkedin,
        String summary,
        List<String> skills,
        List<Experience> experience,
        List<Project> projects,
        List<Education> education,
        List<String> certifications,
        /** What the rewrite changed and why, shown to the user — not printed on the resume. */
        List<String> tailoringNotes,
        /** Job-description keywords the resume genuinely cannot support. */
        List<String> missingKeywords,
        /** 0-100, how well the tailored resume covers the job description. */
        int matchScore) {

    public record Experience(
            String company,
            String role,
            String location,
            String duration,
            List<String> description) {
    }

    public record Project(
            String title,
            String tech,
            List<String> description) {
    }

    public record Education(
            String institution,
            String degree,
            String duration) {
    }
}
