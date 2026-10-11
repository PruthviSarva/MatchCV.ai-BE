package com.tailorresume.resume;

import com.tailorresume.common.ApiException;
import com.tailorresume.resume.extract.ResumeTextExtractor;
import com.tailorresume.user.UserAccount;
import com.tailorresume.user.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

/**
 * Stores and reads the user's base resumes.
 *
 * <p>Every read goes through an owner-scoped lookup, so a resume id guessed or
 * replayed from someone else's session resolves to a 404 rather than leaking
 * the row.
 */
@Service
public class ResumeService {

    private final BaseResumeRepository resumes;
    private final UserRepository users;
    private final ResumeTextExtractor textExtractor;

    public ResumeService(BaseResumeRepository resumes,
                         UserRepository users,
                         ResumeTextExtractor textExtractor) {
        this.resumes = resumes;
        this.users = users;
        this.textExtractor = textExtractor;
    }

    @Transactional
    public BaseResume upload(Long userId, MultipartFile file, String label) {
        if (file == null || file.isEmpty()) {
            throw ApiException.badRequest("Attach a resume file.");
        }

        byte[] content = readBytes(file);
        ResumeTextExtractor.Extracted extracted = textExtractor.extract(
                file.getOriginalFilename(), file.getContentType(), content);

        UserAccount user = users.findById(userId)
                .orElseThrow(() -> ApiException.unauthorized("This account no longer exists."));

        String resolvedLabel = (label == null || label.isBlank())
                ? defaultLabel(file.getOriginalFilename())
                : label.trim();

        return resumes.save(BaseResume.of(
                user,
                resolvedLabel,
                file.getOriginalFilename(),
                extracted.sourceType(),
                extracted.text()));
    }

    @Transactional(readOnly = true)
    public List<BaseResume> listFor(Long userId) {
        return resumes.findByUserIdOrderByCreatedAtDesc(userId);
    }

    @Transactional(readOnly = true)
    public BaseResume getOwned(Long userId, Long resumeId) {
        return resumes.findByIdAndUserId(resumeId, userId)
                .orElseThrow(() -> ApiException.notFound("No such resume."));
    }

    @Transactional
    public void delete(Long userId, Long resumeId) {
        resumes.delete(getOwned(userId, resumeId));
    }

    private static byte[] readBytes(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException unreadable) {
            throw ApiException.badRequest("That upload could not be read. Try again.");
        }
    }

    /** The filename without its extension, which is what the user recognises. */
    private static String defaultLabel(String filename) {
        if (filename == null || filename.isBlank()) {
            return "Resume";
        }
        int dot = filename.lastIndexOf('.');
        String base = dot > 0 ? filename.substring(0, dot) : filename;
        return base.isBlank() ? "Resume" : base.trim();
    }
}
