package com.tailorresume.render;

import com.tailorresume.common.ApiException;
import com.tailorresume.tailor.ResumeData;
import org.apache.poi.xwpf.usermodel.ParagraphAlignment;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.Borders;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

/**
 * Renders the tailored resume as a DOCX.
 *
 * <p>Deliberately plain: one column, no text boxes, no tables, no headers or
 * footers, no images, and the standard section names an ATS looks for. Every
 * one of those is a known parser-breaker — a resume laid out in a table reads
 * back as interleaved nonsense, and anything in a header is often not read at
 * all. DOCX is listed first in the UI for the same reason: it is the format
 * resume parsers handle most reliably.
 */
@Component
public class AtsDocxRenderer {

    private static final Logger log = LoggerFactory.getLogger(AtsDocxRenderer.class);

    private static final String FONT = "Calibri";
    private static final int BODY_SIZE = 10;
    private static final int NAME_SIZE = 18;
    private static final int HEADING_SIZE = 12;

    public byte[] render(ResumeData resume) {
        try (XWPFDocument document = new XWPFDocument();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            header(document, resume);

            if (!resume.summary().isBlank()) {
                section(document, "Professional Summary",
                        () -> paragraph(document, resume.summary()));
            }

            if (!resume.skills().isEmpty()) {
                // One comma-separated line rather than a grid: a skills table is
                // the single most common reason a parser loses the skills.
                section(document, "Skills",
                        () -> paragraph(document, String.join(", ", resume.skills())));
            }

            if (!resume.experience().isEmpty()) {
                section(document, "Experience", () -> {
                    for (ResumeData.Experience role : resume.experience()) {
                        entry(document,
                                ResumeText.roleLine(role.role(), role.company()),
                                ResumeText.contactLine(role.location(), role.duration()),
                                role.description());
                    }
                });
            }

            if (!resume.projects().isEmpty()) {
                section(document, "Projects", () -> {
                    for (ResumeData.Project project : resume.projects()) {
                        entry(document, project.title(), project.tech(), project.description());
                    }
                });
            }

            if (!resume.education().isEmpty()) {
                section(document, "Education", () -> {
                    for (ResumeData.Education study : resume.education()) {
                        entry(document,
                                ResumeText.roleLine(study.degree(), study.institution()),
                                study.duration(),
                                List.of());
                    }
                });
            }

            if (!resume.certifications().isEmpty()) {
                section(document, "Certifications", () -> {
                    for (String certification : resume.certifications()) {
                        bullet(document, certification);
                    }
                });
            }

            document.write(out);
            return out.toByteArray();

        } catch (IOException | RuntimeException failure) {
            log.error("DOCX rendering failed", failure);
            throw ApiException.upstreamFailure("The DOCX could not be generated.");
        }
    }

    private void header(XWPFDocument document, ResumeData resume) {
        XWPFParagraph name = document.createParagraph();
        name.setAlignment(ParagraphAlignment.CENTER);
        XWPFRun run = name.createRun();
        run.setText(ResumeText.ascii(resume.name()));
        run.setBold(true);
        run.setFontSize(NAME_SIZE);
        run.setFontFamily(FONT);

        String contact = ResumeText.contactLine(
                resume.email(), resume.phone(), resume.location(), resume.linkedin());
        if (!contact.isEmpty()) {
            XWPFParagraph line = document.createParagraph();
            line.setAlignment(ParagraphAlignment.CENTER);
            XWPFRun contactRun = line.createRun();
            contactRun.setText(contact);
            contactRun.setFontSize(BODY_SIZE);
            contactRun.setFontFamily(FONT);
        }
    }

    /** Skips the whole section when its body would be empty. */
    private void section(XWPFDocument document, String title, Runnable body) {
        XWPFParagraph heading = document.createParagraph();
        heading.setSpacingBefore(200);
        // A bottom rule on the paragraph, not a drawn line or a one-cell table:
        // it looks like a section divider and carries no layout for a parser to
        // misread.
        heading.setBorderBottom(Borders.SINGLE);

        XWPFRun run = heading.createRun();
        run.setText(title.toUpperCase());
        run.setBold(true);
        run.setFontSize(HEADING_SIZE);
        run.setFontFamily(FONT);

        body.run();
    }

    private void paragraph(XWPFDocument document, String text) {
        String clean = ResumeText.ascii(text);
        if (clean.isEmpty()) {
            return;
        }
        XWPFParagraph paragraph = document.createParagraph();
        XWPFRun run = paragraph.createRun();
        run.setText(clean);
        run.setFontSize(BODY_SIZE);
        run.setFontFamily(FONT);
    }

    private void entry(XWPFDocument document, String title, String subtitle,
                       List<String> bullets) {
        String cleanTitle = ResumeText.ascii(title);
        if (!cleanTitle.isEmpty()) {
            XWPFParagraph paragraph = document.createParagraph();
            paragraph.setSpacingBefore(120);
            XWPFRun run = paragraph.createRun();
            run.setText(cleanTitle);
            run.setBold(true);
            run.setFontSize(BODY_SIZE + 1);
            run.setFontFamily(FONT);
        }

        String cleanSubtitle = ResumeText.ascii(subtitle);
        if (!cleanSubtitle.isEmpty()) {
            XWPFParagraph paragraph = document.createParagraph();
            XWPFRun run = paragraph.createRun();
            run.setText(cleanSubtitle);
            run.setItalic(true);
            run.setFontSize(BODY_SIZE);
            run.setFontFamily(FONT);
        }

        for (String line : bullets) {
            bullet(document, line);
        }
    }

    /**
     * A literal hyphen rather than a numbering definition. Word's list
     * numbering is stored out-of-line and some parsers drop the marker along
     * with the indent; a hyphen always survives.
     */
    private void bullet(XWPFDocument document, String text) {
        String clean = ResumeText.ascii(text);
        if (clean.isEmpty()) {
            return;
        }
        XWPFParagraph paragraph = document.createParagraph();
        paragraph.setIndentationLeft(240);
        XWPFRun run = paragraph.createRun();
        run.setText("- " + clean);
        run.setFontSize(BODY_SIZE);
        run.setFontFamily(FONT);
    }
}
