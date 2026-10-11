package com.tailorresume.render;

import com.tailorresume.common.ApiException;
import com.tailorresume.security.AuthenticatedUser;
import com.tailorresume.tailor.ResumeData;
import com.tailorresume.tailor.TailorService;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Serves the generated resume file.
 *
 * <p>Generated per request rather than stored. The source of truth is the
 * structured resume, generating is cheap and deterministic, and it means a
 * later fix to the layout applies to every past tailoring instead of only to
 * new ones.
 */
@RestController
@RequestMapping("/api/tailorings/{id}/download")
public class DownloadController {

    private static final MediaType DOCX = MediaType.parseMediaType(
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document");

    private final TailorService tailorings;
    private final AtsDocxRenderer docxRenderer;
    private final AtsPdfRenderer pdfRenderer;

    public DownloadController(TailorService tailorings,
                              AtsDocxRenderer docxRenderer,
                              AtsPdfRenderer pdfRenderer) {
        this.tailorings = tailorings;
        this.docxRenderer = docxRenderer;
        this.pdfRenderer = pdfRenderer;
    }

    @GetMapping
    public ResponseEntity<Resource> download(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Long id,
            @RequestParam(value = "format", defaultValue = "docx") String format) {

        ResumeData resume = tailorings.resumeDataFor(user.id(), id);
        String baseName = ResumeText.fileBaseName(resume.name(), id);

        return switch (format.toLowerCase()) {
            case "docx" -> file(docxRenderer.render(resume), baseName + ".docx", DOCX);
            case "pdf" -> file(pdfRenderer.render(resume), baseName + ".pdf",
                    MediaType.APPLICATION_PDF);
            default -> throw ApiException.badRequest(
                    "Unsupported format. Use format=docx or format=pdf.");
        };
    }

    private ResponseEntity<Resource> file(byte[] bytes, String filename, MediaType type) {
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(filename)
                .build();

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .contentType(type)
                .contentLength(bytes.length)
                .body(new ByteArrayResource(bytes));
    }
}
