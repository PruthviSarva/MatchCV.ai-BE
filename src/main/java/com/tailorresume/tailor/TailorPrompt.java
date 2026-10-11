package com.tailorresume.tailor;

/**
 * Builds the rewriting prompt.
 *
 * <p>Kept separate from the client so the wording can be changed and tested
 * without touching the API plumbing.
 *
 * <p>Two things this prompt is deliberately strict about, because they are what
 * makes the output usable rather than impressive:
 * <ul>
 *   <li><b>Truthfulness.</b> Keywords are only used where the resume actually
 *       supports them. Everything else goes in {@code missingKeywords} — a
 *       resume that claims Kubernetes the candidate has never touched fails at
 *       the interview, which is worse than failing at the filter.</li>
 *   <li><b>ATS shape.</b> No tables, no columns, no graphics, standard section
 *       names. The parser that reads this first is not a person.</li>
 * </ul>
 */
final class TailorPrompt {

    private TailorPrompt() {
    }

    static final String SYSTEM = """
            You rewrite resumes so they pass applicant tracking systems and then \
            convince the human who reads them next.

            Hard rules:
            - Every claim must be supported by the original resume. Do not invent \
            employers, titles, dates, degrees, metrics, tools or projects, and do \
            not promote a passing mention into a core skill.
            - Use the job description's own vocabulary wherever the resume \
            genuinely supports it. If the resume says "built REST services" and \
            the posting says "microservices", say microservices. If the resume \
            has no evidence for a requirement, leave it out of the resume and \
            name it in missingKeywords instead.
            - Rewrite bullets rather than copying them: lead with a strong verb, \
            keep the metrics that are already there, and cut filler. Never \
            fabricate a number to make a bullet stronger.
            - Order skills and experience bullets by relevance to this posting, \
            most relevant first.
            - Keep the output ATS-safe: plain text only, no tables, columns, \
            icons, or special characters beyond standard punctuation.

            matchScore is your honest 0-100 estimate of how well the tailored \
            resume covers the posting's stated requirements. Base it on evidence \
            in the resume, not on how well you rewrote it.

            tailoringNotes explains what you changed and what the candidate \
            should know, in a few short sentences. It is shown to the candidate \
            and never printed on the resume.""";

    static String user(String resumeText, String jobDescription) {
        return """
                Tailor this resume to this job description.

                <job_description>
                %s
                </job_description>

                <resume>
                %s
                </resume>"""
                .formatted(jobDescription.strip(), resumeText.strip());
    }
}
