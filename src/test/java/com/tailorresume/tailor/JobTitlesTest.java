package com.tailorresume.tailor;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class JobTitlesTest {

    @Test
    @DisplayName("takes the first useful line as the label")
    void usesFirstLine() {
        assertThat(JobTitles.guess("Senior Backend Engineer\nAcme Corp\nAustin, TX"))
                .isEqualTo("Senior Backend Engineer");
    }

    @Test
    @DisplayName("skips the boilerplate heading job boards paste in first")
    void skipsBoilerplate() {
        assertThat(JobTitles.guess("Job Description\n\nStaff Platform Engineer"))
                .isEqualTo("Staff Platform Engineer");
    }

    @Test
    @DisplayName("strips markdown and bullet markers")
    void stripsMarkers() {
        assertThat(JobTitles.guess("## Data Engineer (Remote)")).isEqualTo("Data Engineer (Remote)");
    }

    @Test
    @DisplayName("truncates a posting that begins with a paragraph")
    void truncatesLongFirstLine() {
        String wall = "We are a fast growing fintech looking for someone who can own our "
                + "entire payments platform end to end and also mentor the team while shipping";

        String title = JobTitles.guess(wall);

        assertThat(title).hasSizeLessThanOrEqualTo(124).endsWith("...");
    }

    @Test
    @DisplayName("falls back rather than returning an empty label")
    void fallsBack() {
        assertThat(JobTitles.guess("   \n\n  ")).isEqualTo("Untitled role");
        assertThat(JobTitles.guess(null)).isEqualTo("Untitled role");
    }
}
