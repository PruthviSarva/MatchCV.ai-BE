package com.tailorresume.render;

import java.util.ArrayList;
import java.util.List;

/**
 * Text helpers shared by both renderers.
 *
 * <p>The sanitizing is an ATS requirement, not a cosmetic one. Smart quotes,
 * en dashes and bullet glyphs are routinely mangled into {@code â€™} by older
 * resume parsers, and the PDF renderer's standard fonts cannot encode them at
 * all. Replacing them with ASCII equivalents costs nothing visually and removes
 * a whole class of "my resume looked fine but the portal garbled it".
 */
final class ResumeText {

    private ResumeText() {
    }

    static String ascii(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        return value
                .replace('‘', '\'').replace('’', '\'')
                .replace('“', '"').replace('”', '"')
                .replace('–', '-').replace('—', '-')
                .replace('•', '-').replace('·', '-')
                .replace(' ', ' ')
                .replace("…", "...")
                // Anything still outside printable ASCII would be dropped by the
                // PDF font encoder; strip it rather than fail the download.
                .replaceAll("[^\\x20-\\x7E]", "")
                .strip();
    }

    /** Joins the non-empty parts of a contact line with a plain separator. */
    static String contactLine(String... parts) {
        List<String> present = new ArrayList<>();
        for (String part : parts) {
            String clean = ascii(part);
            if (!clean.isEmpty()) {
                present.add(clean);
            }
        }
        return String.join(" | ", present);
    }

    /** Joins a role and a company the way every resume does, skipping blanks. */
    static String roleLine(String role, String company) {
        String cleanRole = ascii(role);
        String cleanCompany = ascii(company);
        if (cleanRole.isEmpty()) {
            return cleanCompany;
        }
        if (cleanCompany.isEmpty()) {
            return cleanRole;
        }
        return cleanRole + ", " + cleanCompany;
    }

    /**
     * Greedy word wrap to a character budget.
     *
     * <p>Only the PDF renderer needs this — DOCX wraps text itself — but it
     * lives here so both renderers share one definition of a line.
     */
    static List<String> wrap(String text, int maxCharacters) {
        List<String> lines = new ArrayList<>();
        String clean = ascii(text);
        if (clean.isEmpty()) {
            return lines;
        }

        StringBuilder line = new StringBuilder();
        for (String word : clean.split("\\s+")) {
            if (line.isEmpty()) {
                line.append(word);
            } else if (line.length() + 1 + word.length() <= maxCharacters) {
                line.append(' ').append(word);
            } else {
                lines.add(line.toString());
                line.setLength(0);
                line.append(word);
            }
        }
        if (!line.isEmpty()) {
            lines.add(line.toString());
        }
        return lines;
    }

    /** A filesystem-safe basename for the download, derived from the candidate name. */
    static String fileBaseName(String candidateName, Long tailoringId) {
        String clean = ascii(candidateName).replaceAll("[^A-Za-z0-9]+", "_")
                .replaceAll("^_+|_+$", "");
        if (clean.isEmpty()) {
            return "resume_" + tailoringId;
        }
        return clean + "_resume";
    }
}
