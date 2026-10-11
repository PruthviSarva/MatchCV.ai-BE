package com.tailorresume.tailor;

import java.util.List;
import java.util.Objects;

/**
 * Cleans up a {@link ResumeData} before it is stored or rendered.
 *
 * <p>The schema guarantees the right <em>shape</em>, not sane content: a strict
 * JSON schema cannot stop a null inside a list, a bullet that is only
 * whitespace, a duplicated skill, or a match score of 7000. Normalizing once
 * here means the renderers, the API payload and the tests all see the same
 * cleaned object, rather than each guarding separately.
 */
public final class ResumeDataNormalizer {

    private ResumeDataNormalizer() {
    }

    public static ResumeData normalize(ResumeData raw) {
        if (raw == null) {
            throw new IllegalArgumentException("raw must not be null");
        }

        return new ResumeData(
                text(raw.name()),
                text(raw.email()),
                text(raw.phone()),
                text(raw.location()),
                text(raw.linkedin()),
                text(raw.summary()),
                distinctLines(raw.skills()),
                experience(raw.experience()),
                projects(raw.projects()),
                education(raw.education()),
                distinctLines(raw.certifications()),
                lines(raw.tailoringNotes()),
                distinctLines(raw.missingKeywords()),
                clampScore(raw.matchScore()));
    }

    private static List<ResumeData.Experience> experience(List<ResumeData.Experience> entries) {
        return nullSafe(entries).stream()
                .filter(Objects::nonNull)
                .map(entry -> new ResumeData.Experience(
                        text(entry.company()),
                        text(entry.role()),
                        text(entry.location()),
                        text(entry.duration()),
                        lines(entry.description())))
                // An entry with no company, no role and no bullets is noise.
                .filter(entry -> !entry.company().isEmpty()
                        || !entry.role().isEmpty()
                        || !entry.description().isEmpty())
                .toList();
    }

    private static List<ResumeData.Project> projects(List<ResumeData.Project> entries) {
        return nullSafe(entries).stream()
                .filter(Objects::nonNull)
                .map(entry -> new ResumeData.Project(
                        text(entry.title()),
                        text(entry.tech()),
                        lines(entry.description())))
                .filter(entry -> !entry.title().isEmpty() || !entry.description().isEmpty())
                .toList();
    }

    private static List<ResumeData.Education> education(List<ResumeData.Education> entries) {
        return nullSafe(entries).stream()
                .filter(Objects::nonNull)
                .map(entry -> new ResumeData.Education(
                        text(entry.institution()),
                        text(entry.degree()),
                        text(entry.duration())))
                .filter(entry -> !entry.institution().isEmpty() || !entry.degree().isEmpty())
                .toList();
    }

    /** Trimmed, blank-free, order preserved. */
    private static List<String> lines(List<String> values) {
        return nullSafe(values).stream()
                .filter(Objects::nonNull)
                .map(String::strip)
                .filter(value -> !value.isEmpty())
                .toList();
    }

    /**
     * As {@link #lines} but case-insensitively de-duplicated, keeping the first
     * spelling. A skills list with both "AWS" and "aws" reads as careless.
     */
    private static List<String> distinctLines(List<String> values) {
        return lines(values).stream()
                .collect(java.util.stream.Collectors.toMap(
                        value -> value.toLowerCase(java.util.Locale.ROOT),
                        value -> value,
                        (first, duplicate) -> first,
                        java.util.LinkedHashMap::new))
                .values()
                .stream()
                .toList();
    }

    private static String text(String value) {
        return value == null ? "" : value.strip();
    }

    private static int clampScore(int score) {
        return Math.clamp(score, 0, 100);
    }

    private static <T> List<T> nullSafe(List<T> values) {
        return values == null ? List.of() : values;
    }
}
