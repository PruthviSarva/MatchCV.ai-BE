package com.tailorresume.tailor;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * What the JSON schema cannot guarantee.
 *
 * <p>A strict schema fixes the shape of the model's output, not its content —
 * it will happily return a null inside a list, a whitespace-only bullet, the
 * same skill twice in different case, or a match score of 7000. These are the
 * cases the renderers would otherwise each have to guard against.
 */
class ResumeDataNormalizerTest {

    private static ResumeData raw(List<String> skills, int score) {
        return new ResumeData(
                "  Priya Raman  ", "p@example.com", null, null, null,
                "  A summary.  ",
                skills,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                score);
    }

    @Test
    @DisplayName("trims text and turns nulls into empty strings")
    void cleansText() {
        ResumeData normalized = ResumeDataNormalizer.normalize(raw(List.of(), 50));

        assertThat(normalized.name()).isEqualTo("Priya Raman");
        assertThat(normalized.summary()).isEqualTo("A summary.");
        // Empty rather than null, so the renderers never branch on nullability.
        assertThat(normalized.phone()).isEmpty();
        assertThat(normalized.location()).isEmpty();
    }

    @Test
    @DisplayName("drops nulls and blanks from lists")
    void dropsEmptyEntries() {
        ResumeData normalized = ResumeDataNormalizer.normalize(
                raw(Arrays.asList("Java", null, "   ", "Kafka"), 50));

        assertThat(normalized.skills()).containsExactly("Java", "Kafka");
    }

    @Test
    @DisplayName("de-duplicates skills case-insensitively, keeping the first spelling")
    void dedupesSkills() {
        ResumeData normalized = ResumeDataNormalizer.normalize(
                raw(List.of("AWS", "Java", "aws", "JAVA"), 50));

        assertThat(normalized.skills()).containsExactly("AWS", "Java");
    }

    @Test
    @DisplayName("clamps a match score to 0-100")
    void clampsScore() {
        assertThat(ResumeDataNormalizer.normalize(raw(List.of(), 7000)).matchScore())
                .isEqualTo(100);
        assertThat(ResumeDataNormalizer.normalize(raw(List.of(), -5)).matchScore())
                .isZero();
    }

    @Test
    @DisplayName("discards an experience entry with nothing in it")
    void discardsEmptyExperience() {
        ResumeData withEmpty = new ResumeData(
                "Priya", "p@example.com", "", "", "", "",
                List.of(),
                List.of(
                        new ResumeData.Experience("", "", "", "2020", List.of()),
                        new ResumeData.Experience("Acme", "Engineer", "", "2021", List.of("Did work"))),
                List.of(), List.of(), List.of(), List.of(), List.of(), 60);

        ResumeData normalized = ResumeDataNormalizer.normalize(withEmpty);

        assertThat(normalized.experience()).hasSize(1);
        assertThat(normalized.experience().getFirst().company()).isEqualTo("Acme");
    }

    @Test
    @DisplayName("keeps bullet order — the model ranks by relevance")
    void preservesBulletOrder() {
        ResumeData withBullets = new ResumeData(
                "Priya", "p@example.com", "", "", "", "",
                List.of(),
                List.of(new ResumeData.Experience("Acme", "Engineer", "", "2021",
                        Arrays.asList("Most relevant", null, "Least relevant"))),
                List.of(), List.of(), List.of(), List.of(), List.of(), 60);

        assertThat(ResumeDataNormalizer.normalize(withBullets)
                .experience().getFirst().description())
                .containsExactly("Most relevant", "Least relevant");
    }
}
