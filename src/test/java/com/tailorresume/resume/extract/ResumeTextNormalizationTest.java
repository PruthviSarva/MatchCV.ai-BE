package com.tailorresume.resume.extract;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The normalization applied to every extracted resume.
 *
 * <p>Worth testing directly because it runs on text the model then reads: a
 * page-break artefact or a run of blank lines that survives here is noise the
 * rewritten bullets inherit.
 */
class ResumeTextNormalizationTest {

    @Test
    @DisplayName("collapses the blank-line runs a PDF export leaves behind")
    void collapsesBlankRuns() {
        // One blank line survives as a section separator; the rest are layout.
        assertThat(ResumeTextExtractor.normalize("Experience\n\n\n\n\nAcme Corp"))
                .isEqualTo("Experience\n\nAcme Corp");
    }

    @Test
    @DisplayName("treats a form feed as a line break, not as text")
    void stripsPageBreaks() {
        assertThat(ResumeTextExtractor.normalize("Page one\fPage two"))
                .isEqualTo("Page one\nPage two");
    }

    @Test
    @DisplayName("normalizes the exotic spaces PDF exports emit")
    void normalizesSpaces() {
        String withNbsp = "Java" + ' ' + "Spring" + ' ' + "Boot";

        assertThat(ResumeTextExtractor.normalize(withNbsp)).isEqualTo("Java Spring Boot");
    }

    @Test
    @DisplayName("squeezes the wide gaps a two-column layout leaves mid-line")
    void squeezesInternalRuns() {
        assertThat(ResumeTextExtractor.normalize("Senior Engineer        2021 - Present"))
                .isEqualTo("Senior Engineer 2021 - Present");
    }

    @Test
    @DisplayName("unifies Windows and classic Mac line endings")
    void unifiesLineEndings() {
        assertThat(ResumeTextExtractor.normalize("one\r\ntwo\rthree"))
                .isEqualTo("one\ntwo\nthree");
    }
}
