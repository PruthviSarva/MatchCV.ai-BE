package com.tailorresume.tailor;

/**
 * Derives a short label for a job description so the history list reads like a
 * list of applications rather than a list of timestamps.
 *
 * <p>Deliberately a heuristic on the first useful line rather than a second
 * model call: it only has to be recognisable to the person who pasted the
 * posting, and it must not cost a token or a round trip.
 */
final class JobTitles {

    private static final int MAX_LENGTH = 120;

    private JobTitles() {
    }

    static String guess(String jobDescription) {
        if (jobDescription == null || jobDescription.isBlank()) {
            return "Untitled role";
        }

        for (String line : jobDescription.split("\\R")) {
            String candidate = line.strip()
                    // Postings pasted from job boards often start with a bullet
                    // or a markdown heading marker.
                    .replaceAll("^[#*\\-•\\s]+", "")
                    .strip();

            // Skip boilerplate first lines ("Job Description", "About the role")
            // and anything too short to identify the role.
            if (candidate.length() < 4 || isBoilerplate(candidate)) {
                continue;
            }
            return candidate.length() > MAX_LENGTH
                    ? candidate.substring(0, MAX_LENGTH).strip() + "..."
                    : candidate;
        }

        return "Untitled role";
    }

    private static boolean isBoilerplate(String candidate) {
        String lower = candidate.toLowerCase();
        return lower.equals("job description")
                || lower.equals("job details")
                || lower.equals("about the role")
                || lower.equals("about this role")
                || lower.equals("overview")
                || lower.equals("position");
    }
}
