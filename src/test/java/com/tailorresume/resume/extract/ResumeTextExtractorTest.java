package com.tailorresume.resume.extract;

import com.tailorresume.common.ApiException;
import com.tailorresume.config.AppProperties;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Extraction, tested by round trip: build a real PDF and a real DOCX here, then
 * read them back.
 *
 * <p>Checked-in fixture files would be the alternative, but generating them
 * means the test also proves the two libraries agree with each other — which is
 * the actual risk, since the app both writes and reads these formats.
 */
class ResumeTextExtractorTest {

    private static final String RESUME_BODY = """
            Priya Raman
            priya.raman@example.com | Austin, TX

            EXPERIENCE
            Senior Backend Engineer, Northwind Payments, 2021 - Present
            - Rebuilt the settlement ledger as idempotent Kafka consumers
            - Cut p99 authorization latency from 840ms to 120ms

            EDUCATION
            B.S. Computer Science, University of Texas at Austin
            """;

    private final ResumeTextExtractor extractor = new ResumeTextExtractor(
            List.of(new PdfTextExtractor(), new DocxTextExtractor(), new PlainTextExtractor()),
            properties());

    private static AppProperties properties() {
        return new AppProperties(
                new AppProperties.Jwt("0123456789012345678901234567890123456789012345", null, null, null),
                null, null, null, new AppProperties.Upload(60_000), null);
    }

    @Test
    @DisplayName("reads text back out of a real PDF")
    void extractsPdf() throws IOException {
        ResumeTextExtractor.Extracted extracted =
                extractor.extract("resume.pdf", "application/pdf", pdf(RESUME_BODY));

        assertThat(extracted.sourceType()).isEqualTo("pdf");
        assertThat(extracted.text())
                .contains("Priya Raman")
                .contains("Senior Backend Engineer")
                .contains("Kafka consumers");
    }

    @Test
    @DisplayName("reads text back out of a real DOCX")
    void extractsDocx() throws IOException {
        ResumeTextExtractor.Extracted extracted = extractor.extract(
                "resume.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                docx(RESUME_BODY));

        assertThat(extracted.sourceType()).isEqualTo("docx");
        assertThat(extracted.text()).contains("Priya Raman").contains("Kafka consumers");
    }

    @Test
    @DisplayName("falls back to the content type when the filename has no extension")
    void extractsByContentType() throws IOException {
        ResumeTextExtractor.Extracted extracted =
                extractor.extract("blob", "application/pdf", pdf(RESUME_BODY));

        assertThat(extracted.sourceType()).isEqualTo("pdf");
    }

    @Test
    @DisplayName("reads plain text")
    void extractsPlainText() {
        ResumeTextExtractor.Extracted extracted = extractor.extract(
                "resume.txt", "text/plain; charset=UTF-8",
                RESUME_BODY.getBytes(StandardCharsets.UTF_8));

        assertThat(extracted.sourceType()).isEqualTo("text");
        assertThat(extracted.text()).startsWith("Priya Raman");
    }

    @Test
    @DisplayName("rejects an unsupported file type with advice rather than a crash")
    void rejectsUnsupportedType() {
        assertThatThrownBy(() -> extractor.extract("photo.png", "image/png", new byte[]{1, 2, 3}))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("PDF, DOCX or plain-text");
    }

    @Test
    @DisplayName("rejects a PDF with no text layer instead of tailoring nothing")
    void rejectsScannedPdf() throws IOException {
        // A PDF with a page but no text: exactly what a phone scan produces.
        // Without this check the user would get a confidently rewritten resume
        // built from an empty source.
        byte[] blank;
        try (PDDocument document = new PDDocument();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            document.addPage(new PDPage());
            document.save(out);
            blank = out.toByteArray();
        }

        assertThatThrownBy(() -> extractor.extract("scan.pdf", "application/pdf", blank))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Almost no text");
    }

    @Test
    @DisplayName("rejects an empty upload")
    void rejectsEmptyUpload() {
        assertThatThrownBy(() -> extractor.extract("resume.pdf", "application/pdf", new byte[0]))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("empty");
    }

    @Test
    @DisplayName("rejects a resume past the character ceiling")
    void rejectsOversizedResume() {
        String huge = "Engineer at Acme Corp. ".repeat(4_000);

        assertThatThrownBy(() -> extractor.extract(
                "resume.txt", "text/plain", huge.getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("unusually long");
    }

    private static byte[] pdf(String body) throws IOException {
        try (PDDocument document = new PDDocument();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            PDPage page = new PDPage();
            document.addPage(page);

            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                content.beginText();
                content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 11);
                content.newLineAtOffset(50, 740);
                content.setLeading(14);
                for (String line : body.split("\n")) {
                    content.showText(line);
                    content.newLine();
                }
                content.endText();
            }

            document.save(out);
            return out.toByteArray();
        }
    }

    private static byte[] docx(String body) throws IOException {
        try (XWPFDocument document = new XWPFDocument();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            for (String line : body.split("\n")) {
                document.createParagraph().createRun().setText(line);
            }
            document.write(out);
            return out.toByteArray();
        }
    }
}
