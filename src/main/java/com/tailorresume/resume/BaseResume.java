package com.tailorresume.resume;

import com.tailorresume.user.UserAccount;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

/**
 * A resume the user uploaded, stored as extracted text rather than as the
 * original file.
 *
 * <p>Only the text is kept deliberately: it is the only thing the rewriting
 * needs, the generated output is a fresh ATS-clean document anyway, and not
 * holding the original PDF means a database leak exposes no file the user
 * would recognise as theirs.
 */
@Entity
@Table(name = "base_resumes")
public class BaseResume {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private UserAccount user;

    /** What the user calls it. Defaults to the uploaded filename. */
    @Column(nullable = false, length = 255)
    private String label;

    @Column(name = "source_filename", length = 255)
    private String sourceFilename;

    @Column(name = "source_type", nullable = false, length = 16)
    private String sourceType;

    // Maps to TEXT. Not @Lob: on PostgreSQL @Lob on a String means an oid
    // large object held outside the table and unreadable outside a
    // transaction, which is neither what V1 declares nor what this needs.
    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    @Column(name = "extracted_text", nullable = false)
    private String extractedText;

    @Column(name = "character_count", nullable = false)
    private int characterCount;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected BaseResume() {
        // for JPA
    }

    public static BaseResume of(UserAccount user, String label, String sourceFilename,
                                String sourceType, String extractedText) {
        BaseResume resume = new BaseResume();
        resume.user = user;
        resume.label = label;
        resume.sourceFilename = sourceFilename;
        resume.sourceType = sourceType;
        resume.extractedText = extractedText;
        resume.characterCount = extractedText.length();
        return resume;
    }

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public Long getId() {
        return id;
    }

    public UserAccount getUser() {
        return user;
    }

    public String getLabel() {
        return label;
    }

    public String getSourceFilename() {
        return sourceFilename;
    }

    public String getSourceType() {
        return sourceType;
    }

    public String getExtractedText() {
        return extractedText;
    }

    public int getCharacterCount() {
        return characterCount;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
