package com.tailorresume.resume.extract;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/**
 * Plain text and Markdown.
 *
 * <p>Deliberately not a catch-all: an upload with no recognised extension or
 * content type is rejected with a clear message rather than read as text, so a
 * stray image does not become a resume full of mojibake.
 */
@Component
public class PlainTextExtractor implements TextExtractor {

    @Override
    public String sourceType() {
        return "text";
    }

    @Override
    public boolean supports(String filename, String contentType) {
        return Formats.hasExtension(filename, ".txt")
                || Formats.hasExtension(filename, ".md")
                || Formats.isContentType(contentType, "text/plain")
                || Formats.isContentType(contentType, "text/markdown");
    }

    @Override
    public String extract(byte[] content) {
        return new String(content, StandardCharsets.UTF_8);
    }
}
