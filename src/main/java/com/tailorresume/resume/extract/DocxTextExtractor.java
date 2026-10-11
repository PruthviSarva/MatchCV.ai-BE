package com.tailorresume.resume.extract;

import com.tailorresume.common.ApiException;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.IOException;

@Component
public class DocxTextExtractor implements TextExtractor {

    private static final Logger log = LoggerFactory.getLogger(DocxTextExtractor.class);

    @Override
    public String sourceType() {
        return "docx";
    }

    @Override
    public boolean supports(String filename, String contentType) {
        return Formats.hasExtension(filename, ".docx")
                || Formats.isContentType(contentType,
                        "application/vnd.openxmlformats-officedocument.wordprocessingml.document");
    }

    @Override
    public String extract(byte[] content) {
        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(content))) {
            StringBuilder text = new StringBuilder();

            for (XWPFParagraph paragraph : document.getParagraphs()) {
                text.append(paragraph.getText()).append('\n');
            }

            // Plenty of resume templates put the whole layout in a table, and
            // POI does not return table text with the paragraphs. Skipping
            // these would silently extract an empty resume.
            for (XWPFTable table : document.getTables()) {
                for (XWPFTableRow row : table.getRows()) {
                    for (XWPFTableCell cell : row.getTableCells()) {
                        text.append(cell.getText()).append('\n');
                    }
                }
            }

            return text.toString();
        } catch (IOException | RuntimeException unreadable) {
            log.warn("DOCX text extraction failed", unreadable);
            throw ApiException.badRequest(
                    "That DOCX could not be read. If it is an older .doc file, "
                            + "save it as .docx and try again.");
        }
    }
}
