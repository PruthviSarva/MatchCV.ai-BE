package com.tailorresume.tailor;

import com.tailorresume.resume.BaseResume;
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
 * One tailoring: a base resume rewritten against one job description.
 *
 * <p>The resume itself is stored as the serialized {@link ResumeData} JSON. It
 * is a document, not something the database needs to query inside, and keeping
 * it whole means a schema change to {@code ResumeData} does not require a
 * migration — only the renderer has to tolerate older rows.
 *
 * <p>The job description text is kept too. Without it, a saved tailoring is
 * impossible to interpret six weeks later, and re-running one would mean asking
 * the user to find the posting again.
 */
@Entity
@Table(name = "tailored_resumes")
public class TailoredResume {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private UserAccount user;

    /**
     * Nullable on purpose: deleting a base resume must not delete the tailored
     * copies already produced from it.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "base_resume_id")
    private BaseResume baseResume;

    /** Derived from the posting for the history list, e.g. "Backend Engineer at Acme". */
    @Column(name = "job_title", length = 255)
    private String jobTitle;

    // Both long text fields map to TEXT. Not @Lob: on PostgreSQL @Lob on a
    // String means an oid large object held outside the table and unreadable
    // outside a transaction, which is not what V1 declares.
    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    @Column(name = "job_description", nullable = false)
    private String jobDescription;

    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    @Column(name = "resume_json", nullable = false)
    private String resumeJson;

    @Column(name = "match_score", nullable = false)
    private int matchScore;

    @Column(name = "model_name", nullable = false, length = 64)
    private String modelName;

    /**
     * The billing period this tailoring was spent in, copied from the
     * subscription at the moment the allowance was checked.
     *
     * <p>Stamped rather than derived from {@code createdAt}, so the quota count
     * is an equality match on a period instead of a timestamp comparison — see
     * {@link com.tailorresume.billing.TailoringUsage}.
     */
    @Column(name = "billing_period_start", nullable = false)
    private Instant billingPeriodStart;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected TailoredResume() {
        // for JPA
    }

    public static TailoredResume of(UserAccount user, BaseResume baseResume, String jobTitle,
                                    String jobDescription, String resumeJson, int matchScore,
                                    String modelName, Instant billingPeriodStart) {
        TailoredResume tailored = new TailoredResume();
        tailored.user = user;
        tailored.baseResume = baseResume;
        tailored.jobTitle = jobTitle;
        tailored.jobDescription = jobDescription;
        tailored.resumeJson = resumeJson;
        tailored.matchScore = matchScore;
        tailored.modelName = modelName;
        tailored.billingPeriodStart = billingPeriodStart;
        return tailored;
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

    public BaseResume getBaseResume() {
        return baseResume;
    }

    public String getJobTitle() {
        return jobTitle;
    }

    public String getJobDescription() {
        return jobDescription;
    }

    public String getResumeJson() {
        return resumeJson;
    }

    public int getMatchScore() {
        return matchScore;
    }

    public String getModelName() {
        return modelName;
    }

    public Instant getBillingPeriodStart() {
        return billingPeriodStart;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
