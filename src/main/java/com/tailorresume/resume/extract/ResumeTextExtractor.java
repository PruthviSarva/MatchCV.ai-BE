package com.tailorresume.resume.extract;

import com.tailorresume.common.ApiException;
import com.tailorresume.config.AppProperties;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Picks the right {@link TextExtractor} for an upload and normalizes what comes
 * back.
 *
 * <p>The normalization is not cosmetic. PDF extraction produces ragged
 * whitespace, non-breaking spaces and page-break artefacts; feeding that to the
 * model wastes tokens and makes the rewritten bullets inherit the noise.
 */
@Service
public class ResumeTextExtractor {

    private final List<TextExtractor> extractors;
    private final int maxCharacters;

    public ResumeTextExtractor(List<TextExtractor> extractors, AppProperties properties) {
        this.extractors = extractors;
        this.maxCharacters = properties.upload().maxExtractedCharacters();
    }

    public record Extracted(String sourceType, String text) {
    }

    public Extracted extract(String filename, String contentType, byte[] content) {
        if (content == null || content.length == 0) {
            throw ApiException.badRequest("That file is empty.");
        }

        TextExtractor extractor = extractors.stream()
                .filter(candidate -> candidate.supports(filename, contentType))
                .findFirst()
                .orElseThrow(() -> ApiException.badRequest(
                        "Unsupported file type. Upload a PDF, DOCX or plain-text resume."));

        String text = normalize(extractor.extract(content));

        if (text.length() < 100) {
            // Almost always a scanned or image-only PDF: the file opens fine in
            // a viewer but carries no text layer, so there is nothing to tailor.
            throw ApiException.badRequest(
                    "Almost no text could be read from that file. If it is a scan or an "
                            + "image-based PDF, upload a text-based export or a DOCX instead.");
        }
        if (text.length() > maxCharacters) {
            throw ApiException.badRequest(
                    "That resume is unusually long (" + text.length()
                            + " characters). Trim it to under " + maxCharacters + ".");
        }

        return new Extracted(extractor.sourceType(), text);
    }

    /**
     * Collapses runs of blank lines and strips trailing spaces, leaving the line
     * structure the model relies on to tell a heading from a bullet.
     */
    static String normalize(String raw) {
        String unified = raw
                .replace("\r\n", "\n")
                .replace('\r', '\n')
                // Non-breaking and other exotic spaces that PDF exports love.
                .replace(' ', ' ')
                .replace(' ', ' ')
                .replace(' ', ' ')
                // Form feeds mark page breaks and would otherwise read as text.
                .replace("\f", "\n");

        StringBuilder out = new StringBuilder(unified.length());
        int consecutiveBlanks = 0;

        for (String line : unified.split("\n", -1)) {
            String trimmed = line.replaceAll("[ \t]+$", "").replaceAll("[ \t]{2,}", " ");
            if (trimmed.isBlank()) {
                consecutiveBlanks++;
                // One blank line is a section separator and worth keeping; more
                // than that is just PDF layout leaking through.
                if (consecutiveBlanks > 1) {
                    continue;
                }
                out.append('\n');
            } else {
                consecutiveBlanks = 0;
                out.append(trimmed).append('\n');
            }
        }

        return out.toString().strip();
    }
}
