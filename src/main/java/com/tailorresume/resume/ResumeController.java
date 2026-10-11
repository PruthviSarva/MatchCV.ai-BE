package com.tailorresume.resume;

import com.tailorresume.resume.ResumeDtos.BaseResumeDetail;
import com.tailorresume.resume.ResumeDtos.BaseResumeSummary;
import com.tailorresume.security.AuthenticatedUser;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/resumes")
public class ResumeController {

    private final ResumeService resumes;

    public ResumeController(ResumeService resumes) {
        this.resumes = resumes;
    }

    /**
     * Upload a resume. Parsing happens here, server-side, so the browser needs
     * no PDF library and every format is handled the same way.
     */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<BaseResumeDetail> upload(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestPart("file") MultipartFile file,
            @RequestParam(value = "label", required = false) String label) {

        BaseResume saved = resumes.upload(user.id(), file, label);
        return ResponseEntity.status(HttpStatus.CREATED).body(BaseResumeDetail.from(saved));
    }

    @GetMapping
    public List<BaseResumeSummary> list(@AuthenticationPrincipal AuthenticatedUser user) {
        return resumes.listFor(user.id()).stream().map(BaseResumeSummary::from).toList();
    }

    @GetMapping("/{id}")
    public BaseResumeDetail get(@AuthenticationPrincipal AuthenticatedUser user,
                                @PathVariable Long id) {
        return BaseResumeDetail.from(resumes.getOwned(user.id(), id));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal AuthenticatedUser user,
                                       @PathVariable Long id) {
        resumes.delete(user.id(), id);
        return ResponseEntity.noContent().build();
    }
}
