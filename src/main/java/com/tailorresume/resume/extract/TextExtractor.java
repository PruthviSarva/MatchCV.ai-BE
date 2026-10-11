package com.tailorresume.resume.extract;

/** Pulls plain text out of one upload format. */
public interface TextExtractor {

    /** Short name stored on the resume row: {@code pdf}, {@code docx}, {@code text}. */
    String sourceType();

    boolean supports(String filename, String contentType);

    /**
     * @throws com.tailorresume.common.ApiException 400 when the bytes are not
     *         readable as this format
     */
    String extract(byte[] content);
}
