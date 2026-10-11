package com.tailorresume.support;

import com.tailorresume.tailor.ResumeData;

import java.util.List;

/** Fixtures shared by the renderer and pipeline tests. */
public final class SampleResumes {

    private SampleResumes() {
    }

    /**
     * A complete resume with every section populated, and deliberately full of
     * the characters that break resume parsers — curly quotes, an en dash and a
     * bullet glyph — so the renderers are tested on realistic model output
     * rather than on pre-cleaned text.
     */
    public static ResumeData complete() {
        return new ResumeData(
                "Priya Raman",
                "priya.raman@example.com",
                "+1 555 0142",
                "Austin, TX",
                "linkedin.com/in/priyaraman",
                "Backend engineer with six years building payment services on AWS "
                        + "— owned the ledger that settles $40M a month.",
                List.of("Java", "Spring Boot", "PostgreSQL", "AWS", "Kafka", "Terraform"),
                List.of(new ResumeData.Experience(
                        "Northwind Payments",
                        "Senior Backend Engineer",
                        "Austin, TX",
                        "2021 - Present",
                        List.of(
                                "Rebuilt the settlement ledger as idempotent Kafka consumers, "
                                        + "cutting duplicate postings to zero",
                                "Cut p99 authorization latency from 840ms to 120ms by "
                                        + "replacing a synchronous fraud call with a cached score"))),
                List.of(new ResumeData.Project(
                        "Reconciler",
                        "Java, PostgreSQL",
                        List.of("Open-source tool that diffs ledger and bank statements"))),
                List.of(new ResumeData.Education(
                        "University of Texas at Austin",
                        "B.S. Computer Science",
                        "2015 - 2019")),
                List.of("AWS Certified Solutions Architect – Associate"),
                List.of("Reordered skills to lead with Java and Kafka, which the posting names first."),
                List.of("Kubernetes", "Go"),
                78);
    }

    /**
     * The other end of the range: a one-line resume with every optional section
     * empty. The renderers must produce a valid file rather than an empty page
     * or a stray heading.
     */
    public static ResumeData minimal() {
        return new ResumeData(
                "Sam Doe", "sam@example.com", "", "", "",
                "",
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                0);
    }
}
