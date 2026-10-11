package com.tailorresume.resume.extract;

final class Formats {

    private Formats() {
    }

    static boolean hasExtension(String filename, String extension) {
        return filename != null && filename.toLowerCase().endsWith(extension);
    }

    /**
     * Matches the media type while ignoring any parameters — browsers send
     * things like {@code text/plain; charset=UTF-8}.
     */
    static boolean isContentType(String contentType, String expected) {
        if (contentType == null) {
            return false;
        }
        String bare = contentType.split(";")[0].trim();
        return bare.equalsIgnoreCase(expected);
    }
}
