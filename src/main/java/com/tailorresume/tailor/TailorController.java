package com.tailorresume.tailor;

import com.tailorresume.security.AuthenticatedUser;
import com.tailorresume.tailor.TailorDtos.TailorRequest;
import com.tailorresume.tailor.TailorDtos.TailoredResumeDetail;
import com.tailorresume.tailor.TailorDtos.TailoredResumeSummary;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/tailorings")
public class TailorController {

    private final TailorService tailorings;

    public TailorController(TailorService tailorings) {
        this.tailorings = tailorings;
    }

    /**
     * Rewrite a resume against a job description.
     *
     * <p>A slow call by design — the model thinks, then writes a whole resume.
     * The frontend should show progress rather than a spinner with a timeout.
     */
    @PostMapping
    public ResponseEntity<TailoredResumeDetail> tailor(
            @AuthenticationPrincipal AuthenticatedUser user,
            @Valid @RequestBody TailorRequest request) {

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(tailorings.tailor(user.id(), request));
    }

    @GetMapping
    public List<TailoredResumeSummary> list(@AuthenticationPrincipal AuthenticatedUser user) {
        return tailorings.listFor(user.id()).stream().map(TailoredResumeSummary::from).toList();
    }

    @GetMapping("/{id}")
    public TailoredResumeDetail get(@AuthenticationPrincipal AuthenticatedUser user,
                                    @PathVariable Long id) {
        return tailorings.detailFor(user.id(), id);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal AuthenticatedUser user,
                                       @PathVariable Long id) {
        tailorings.delete(user.id(), id);
        return ResponseEntity.noContent().build();
    }
}
