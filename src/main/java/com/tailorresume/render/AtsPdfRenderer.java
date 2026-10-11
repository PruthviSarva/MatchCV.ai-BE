package com.tailorresume.render;

import com.tailorresume.common.ApiException;
import com.tailorresume.tailor.ResumeData;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

/**
 * Renders the tailored resume as a PDF.
 *
 * <p>Laid out by hand rather than through an HTML-to-PDF engine. That sounds
 * like the harder path, but it is the reason the output is ATS-safe: the text
 * is drawn in reading order as a single column with the standard fonts, so a
 * parser extracts it back in exactly the order a human reads it. An HTML
 * renderer would also have dragged in a second, conflicting PDFBox version.
 *
 * <p>Standard-14 fonts carry no embedded font program, which keeps the file
 * small and universally readable — at the cost of WinAnsi-only characters,
 * which is why everything goes through {@link ResumeText#ascii}.
 */
@Component
public class AtsPdfRenderer {

    private static final Logger log = LoggerFactory.getLogger(AtsPdfRenderer.class);

    private static final float MARGIN = 54f;          // 0.75 inch
    private static final float BODY_SIZE = 10f;
    private static final float NAME_SIZE = 18f;
    private static final float HEADING_SIZE = 11.5f;
    private static final float LINE_GAP = 1.35f;      // multiple of font size
    private static final int BODY_WRAP = 98;          // characters per body line
    private static final int BULLET_WRAP = 94;

    public byte[] render(ResumeData resume) {
        try (PDDocument document = new PDDocument();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            Cursor cursor = new Cursor(document);

            cursor.centered(ResumeText.ascii(resume.name()), NAME_SIZE, true);
            String contact = ResumeText.contactLine(
                    resume.email(), resume.phone(), resume.location(), resume.linkedin());
            if (!contact.isEmpty()) {
                cursor.centered(contact, BODY_SIZE, false);
            }

            if (!resume.summary().isBlank()) {
                cursor.heading("PROFESSIONAL SUMMARY");
                cursor.body(resume.summary());
            }

            if (!resume.skills().isEmpty()) {
                cursor.heading("SKILLS");
                cursor.body(String.join(", ", resume.skills()));
            }

            if (!resume.experience().isEmpty()) {
                cursor.heading("EXPERIENCE");
                for (ResumeData.Experience role : resume.experience()) {
                    cursor.entry(ResumeText.roleLine(role.role(), role.company()),
                            ResumeText.contactLine(role.location(), role.duration()),
                            role.description());
                }
            }

            if (!resume.projects().isEmpty()) {
                cursor.heading("PROJECTS");
                for (ResumeData.Project project : resume.projects()) {
                    cursor.entry(project.title(), project.tech(), project.description());
                }
            }

            if (!resume.education().isEmpty()) {
                cursor.heading("EDUCATION");
                for (ResumeData.Education study : resume.education()) {
                    cursor.entry(ResumeText.roleLine(study.degree(), study.institution()),
                            study.duration(), List.of());
                }
            }

            if (!resume.certifications().isEmpty()) {
                cursor.heading("CERTIFICATIONS");
                for (String certification : resume.certifications()) {
                    cursor.bullet(certification);
                }
            }

            cursor.close();
            document.save(out);
            return out.toByteArray();

        } catch (IOException | RuntimeException failure) {
            log.error("PDF rendering failed", failure);
            throw ApiException.upstreamFailure("The PDF could not be generated.");
        }
    }

    /**
     * Walks down the page, opening a new one when it runs out of room.
     *
     * <p>Exists because PDFBox has no concept of text flow: every string is
     * drawn at an absolute position, so something has to own "where am I" and
     * "do I still fit".
     */
    private static final class Cursor implements AutoCloseable {

        private static final PDType1Font REGULAR =
                new PDType1Font(Standard14Fonts.FontName.HELVETICA);
        private static final PDType1Font BOLD =
                new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
        private static final PDType1Font ITALIC =
                new PDType1Font(Standard14Fonts.FontName.HELVETICA_OBLIQUE);

        private final PDDocument document;
        private PDPage page;
        private PDPageContentStream content;
        private float y;

        Cursor(PDDocument document) throws IOException {
            this.document = document;
            newPage();
        }

        private void newPage() throws IOException {
            if (content != null) {
                content.close();
            }
            page = new PDPage(PDRectangle.LETTER);
            document.addPage(page);
            content = new PDPageContentStream(document, page);
            y = page.getMediaBox().getHeight() - MARGIN;
        }

        private float usableWidth() {
            return page.getMediaBox().getWidth() - (2 * MARGIN);
        }

        /** Opens a new page when the next line would cross the bottom margin. */
        private void ensureRoom(float needed) throws IOException {
            if (y - needed < MARGIN) {
                newPage();
            }
        }

        private void draw(String text, float size, PDType1Font font, float x) throws IOException {
            ensureRoom(size * LINE_GAP);
            content.beginText();
            content.setFont(font, size);
            content.newLineAtOffset(x, y);
            content.showText(text);
            content.endText();
            y -= size * LINE_GAP;
        }

        void centered(String text, float size, boolean bold) throws IOException {
            if (text.isEmpty()) {
                return;
            }
            PDType1Font font = bold ? BOLD : REGULAR;
            float width = font.getStringWidth(text) / 1000 * size;
            float x = MARGIN + (usableWidth() - width) / 2;
            draw(text, size, font, Math.max(MARGIN, x));
        }

        void heading(String title) throws IOException {
            // Keep a heading with at least its first line of content, so a
            // section never ends up orphaned at the foot of a page.
            ensureRoom(HEADING_SIZE * LINE_GAP * 3);
            y -= 8;
            draw(title, HEADING_SIZE, BOLD, MARGIN);

            // The rule under the heading is drawn, not a character, so it
            // contributes nothing to extracted text.
            ensureRoom(6);
            float lineY = y + 4;
            content.setLineWidth(0.6f);
            content.moveTo(MARGIN, lineY);
            content.lineTo(MARGIN + usableWidth(), lineY);
            content.stroke();
            y -= 4;
        }

        void body(String text) throws IOException {
            for (String line : ResumeText.wrap(text, BODY_WRAP)) {
                draw(line, BODY_SIZE, REGULAR, MARGIN);
            }
        }

        void entry(String title, String subtitle, List<String> bullets) throws IOException {
            y -= 4;
            String cleanTitle = ResumeText.ascii(title);
            if (!cleanTitle.isEmpty()) {
                for (String line : ResumeText.wrap(cleanTitle, BODY_WRAP)) {
                    draw(line, BODY_SIZE + 1, BOLD, MARGIN);
                }
            }
            String cleanSubtitle = ResumeText.ascii(subtitle);
            if (!cleanSubtitle.isEmpty()) {
                for (String line : ResumeText.wrap(cleanSubtitle, BODY_WRAP)) {
                    draw(line, BODY_SIZE, ITALIC, MARGIN);
                }
            }
            for (String bullet : bullets) {
                bullet(bullet);
            }
        }

        void bullet(String text) throws IOException {
            List<String> lines = ResumeText.wrap(text, BULLET_WRAP);
            for (int i = 0; i < lines.size(); i++) {
                // "- " on the first line only; continuations align under the text.
                String line = i == 0 ? "- " + lines.get(i) : "  " + lines.get(i);
                draw(line, BODY_SIZE, REGULAR, MARGIN + 10);
            }
        }

        @Override
        public void close() throws IOException {
            if (content != null) {
                content.close();
                content = null;
            }
        }
    }
}
