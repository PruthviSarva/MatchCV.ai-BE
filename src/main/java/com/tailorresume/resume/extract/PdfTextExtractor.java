package com.tailorresume.resume.extract;

import com.tailorresume.common.ApiException;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class PdfTextExtractor implements TextExtractor {

    private static final Logger log = LoggerFactory.getLogger(PdfTextExtractor.class);

    @Override
    public String sourceType() {
        return "pdf";
    }

    @Override
    public boolean supports(String filename, String contentType) {
        return Formats.hasExtension(filename, ".pdf")
                || Formats.isContentType(contentType, "application/pdf");
    }

    @Override
    public String extract(byte[] content) {
        try (PDDocument document = Loader.loadPDF(content)) {
            PDFTextStripper stripper = new PDFTextStripper();
            // Reading order matters: a two-column resume extracted in raw
            // stream order interleaves the columns into nonsense.
            stripper.setSortByPosition(true);
            return stripper.getText(document);
        } catch (IOException unreadable) {
            log.warn("PDF text extraction failed", unreadable);
            throw ApiException.badRequest(
                    "That PDF could not be read. Try exporting it again, or upload a DOCX.");
        }
    }
}
