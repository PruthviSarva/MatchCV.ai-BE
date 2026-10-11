package com.tailorresume.render;

import com.tailorresume.support.SampleResumes;
import com.tailorresume.tailor.ResumeData;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The renderers, tested the way an applicant tracking system reads them: render
 * the resume, then extract the text back out and assert on what survived.
 *
 * <p>This is the only test shape that actually proves the output is
 * ATS-friendly. Asserting the file is non-empty, or that it opens, would pass
 * for a resume laid out in a table that a parser turns into gibberish.
 */
class AtsRendererTest {

    private final AtsDocxRenderer docx = new AtsDocxRenderer();
    private final AtsPdfRenderer pdf = new AtsPdfRenderer();

    private static String textOf(byte[] pdfBytes) throws IOException {
        try (PDDocument document = Loader.loadPDF(pdfBytes)) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            return stripper.getText(document);
        }
    }

    private static String textOf(XWPFDocument document) {
        StringBuilder text = new StringBuilder();
        for (XWPFParagraph paragraph : document.getParagraphs()) {
            text.append(paragraph.getText()).append('\n');
        }
        return text.toString();
    }

    @Test
    @DisplayName("every section survives the PDF round trip, in reading order")
    void pdfKeepsEverySection() throws IOException {
        ResumeData resume = SampleResumes.complete();

        String extracted = textOf(pdf.render(resume));

        assertThat(extracted)
                .contains("Priya Raman")
                .contains("priya.raman@example.com")
                .contains("PROFESSIONAL SUMMARY")
                .contains("SKILLS")
                .contains("EXPERIENCE")
                .contains("PROJECTS")
                .contains("EDUCATION")
                .contains("CERTIFICATIONS")
                .contains("Senior Backend Engineer")
                .contains("Northwind Payments");

        // Reading order is the thing a multi-column layout breaks: the header
        // must come out before the sections, and experience before education.
        assertThat(extracted.indexOf("Priya Raman"))
                .isLessThan(extracted.indexOf("EXPERIENCE"));
        assertThat(extracted.indexOf("EXPERIENCE"))
                .isLessThan(extracted.indexOf("EDUCATION"));
    }

    @Test
    @DisplayName("a wrapped bullet survives the PDF round trip as one sentence")
    void pdfKeepsWrappedBulletsReadable() throws IOException {
        String extracted = textOf(pdf.render(SampleResumes.complete()));

        // The bullet is long enough to wrap, so this only passes if the
        // continuation line lands directly under the first.
        assertThat(extracted.replaceAll("\\s+", " "))
                .contains("Cut p99 authorization latency from 840ms to 120ms");
    }

    @Test
    @DisplayName("the PDF carries no characters an older parser would mangle")
    void pdfIsAsciiOnly() throws IOException {
        // The fixture deliberately contains an en dash and curly quotes.
        String extracted = textOf(pdf.render(SampleResumes.complete()));

        assertThat(extracted.replaceAll("[\\x20-\\x7E\\s]", "")).isEmpty();
    }

    @Test
    @DisplayName("every section survives the DOCX round trip")
    void docxKeepsEverySection() throws IOException {
        byte[] rendered = docx.render(SampleResumes.complete());

        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(rendered))) {
            String extracted = textOf(document);

            assertThat(extracted)
                    .contains("Priya Raman")
                    .contains("PROFESSIONAL SUMMARY")
                    .contains("EXPERIENCE")
                    .contains("Senior Backend Engineer, Northwind Payments")
                    .contains("Rebuilt the settlement ledger");

            // No tables at all: a resume laid out in a table is the single most
            // common reason a parser returns interleaved nonsense.
            assertThat(document.getTables()).isEmpty();
            // Nothing in the header or footer, which many parsers never read.
            assertThat(document.getHeaderList()).isEmpty();
            assertThat(document.getFooterList()).isEmpty();
        }
    }

    @Test
    @DisplayName("the tailoring notes are never printed on the resume")
    void notesAreNotPartOfTheDocument() throws IOException {
        ResumeData resume = SampleResumes.complete();
        String note = resume.tailoringNotes().getFirst();
        String missing = resume.missingKeywords().getFirst();

        String fromPdf = textOf(pdf.render(resume));
        String fromDocx;
        try (XWPFDocument document =
                     new XWPFDocument(new ByteArrayInputStream(docx.render(resume)))) {
            fromDocx = textOf(document);
        }

        // These are advice for the candidate, not resume content. Printing them
        // would send "Reordered skills to lead with Java" to the employer.
        assertThat(fromPdf).doesNotContain(note).doesNotContain(missing);
        assertThat(fromDocx).doesNotContain(note).doesNotContain(missing);
    }

    @Test
    @DisplayName("an almost-empty resume still renders a valid file with no stray headings")
    void handlesMinimalResume() throws IOException {
        ResumeData resume = SampleResumes.minimal();

        String fromPdf = textOf(pdf.render(resume));
        assertThat(fromPdf).contains("Sam Doe");
        // An empty section must not leave its heading behind.
        assertThat(fromPdf)
                .doesNotContain("SKILLS")
                .doesNotContain("EXPERIENCE")
                .doesNotContain("PROFESSIONAL SUMMARY");

        try (XWPFDocument document =
                     new XWPFDocument(new ByteArrayInputStream(docx.render(resume)))) {
            assertThat(textOf(document)).contains("Sam Doe").doesNotContain("SKILLS");
        }
    }

    @Test
    @DisplayName("a long resume paginates instead of running off the page")
    void paginatesLongResume() throws IOException {
        ResumeData base = SampleResumes.complete();
        // Twelve roles with two bullets each will not fit on one page.
        var manyRoles = new java.util.ArrayList<ResumeData.Experience>();
        for (int i = 0; i < 12; i++) {
            manyRoles.add(new ResumeData.Experience(
                    "Company " + i, "Backend Engineer", "Remote", "20" + (10 + i),
                    java.util.List.of(
                            "Shipped service number " + i + " with a measurable latency win",
                            "Mentored two engineers and ran the on-call rotation")));
        }
        ResumeData longResume = new ResumeData(
                base.name(), base.email(), base.phone(), base.location(), base.linkedin(),
                base.summary(), base.skills(), manyRoles, base.projects(), base.education(),
                base.certifications(), base.tailoringNotes(), base.missingKeywords(),
                base.matchScore());

        byte[] rendered = pdf.render(longResume);

        try (PDDocument document = Loader.loadPDF(rendered)) {
            assertThat(document.getNumberOfPages()).isGreaterThan(1);
        }
        // Nothing dropped at the page boundary: the last role still appears.
        assertThat(textOf(rendered)).contains("Company 11");
    }

    @Test
    @DisplayName("the download filename is derived from the candidate name, safely")
    void buildsSafeFilename() {
        assertThat(ResumeText.fileBaseName("Priya Raman", 7L)).isEqualTo("Priya_Raman_resume");
        assertThat(ResumeText.fileBaseName("P. R. O'Neill-Smith", 7L))
                .isEqualTo("P_R_O_Neill_Smith_resume");
        // No name to work with: fall back rather than produce ".pdf".
        assertThat(ResumeText.fileBaseName("", 7L)).isEqualTo("resume_7");
    }
}
