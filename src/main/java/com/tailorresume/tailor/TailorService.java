package com.tailorresume.tailor;

import com.tailorresume.billing.QuotaService;
import com.tailorresume.common.ApiException;
import com.tailorresume.resume.BaseResume;
import com.tailorresume.resume.ResumeService;
import com.tailorresume.tailor.TailorDtos.TailorRequest;
import com.tailorresume.tailor.TailorDtos.TailoredResumeDetail;
import com.tailorresume.user.UserAccount;
import com.tailorresume.user.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Runs a tailoring and records it.
 *
 * <p>The quota is checked before the model call, not after. Charging for work
 * the user was not entitled to and then refusing to show it would be the worst
 * of both; a request that cannot be served should cost nothing.
 *
 * <p>The model call deliberately happens <em>outside</em> a transaction. It can
 * take minutes, and holding a database connection open for its duration would
 * exhaust the pool under any real concurrency.
 */
@Service
public class TailorService {

    private final TailoredResumeRepository tailored;
    private final UserRepository users;
    private final ResumeService resumes;
    private final ResumeTailorClient tailorClient;
    private final ResumeJsonCodec codec;
    private final QuotaService quotas;

    public TailorService(TailoredResumeRepository tailored,
                         UserRepository users,
                         ResumeService resumes,
                         ResumeTailorClient tailorClient,
                         ResumeJsonCodec codec,
                         QuotaService quotas) {
        this.tailored = tailored;
        this.users = users;
        this.resumes = resumes;
        this.tailorClient = tailorClient;
        this.codec = codec;
        this.quotas = quotas;
    }

    public TailoredResumeDetail tailor(Long userId, TailorRequest request) {
        // Returns the period the allowance came out of, which the new row is
        // then stamped with — so an upgrade moments later cannot make this
        // tailoring count against the plan the user just paid for.
        Instant billingPeriod = quotas.claimTailoringAllowance(userId);

        Source source = resolveSource(userId, request);
        ResumeData result = tailorClient.tailor(source.text(), request.jobDescription());

        return detail(
                persist(userId, source.baseResumeId(), request, result, billingPeriod), result);
    }

    @Transactional(readOnly = true)
    public List<TailoredResume> listFor(Long userId) {
        return tailored.findByUserIdOrderByCreatedAtDesc(userId);
    }

    @Transactional(readOnly = true)
    public TailoredResume getOwned(Long userId, Long id) {
        return tailored.findByIdAndUserId(id, userId)
                .orElseThrow(() -> ApiException.notFound("No such tailored resume."));
    }

    @Transactional(readOnly = true)
    public TailoredResumeDetail detailFor(Long userId, Long id) {
        TailoredResume entity = getOwned(userId, id);
        return detail(entity, codec.read(entity.getResumeJson()));
    }

    /** The structured resume on its own — what the renderers need. */
    @Transactional(readOnly = true)
    public ResumeData resumeDataFor(Long userId, Long id) {
        return codec.read(getOwned(userId, id).getResumeJson());
    }

    @Transactional
    public void delete(Long userId, Long id) {
        tailored.delete(getOwned(userId, id));
    }

    private record Source(String text, Long baseResumeId) {
    }

    private Source resolveSource(Long userId, TailorRequest request) {
        if (request.baseResumeId() != null) {
            BaseResume base = resumes.getOwned(userId, request.baseResumeId());
            return new Source(base.getExtractedText(), base.getId());
        }

        String inline = request.resumeText();
        if (inline == null || inline.strip().length() < 100) {
            throw ApiException.badRequest(
                    "Choose a saved resume or paste your resume text first.");
        }
        return new Source(inline.strip(), null);
    }

    /**
     * No {@code @Transactional} here on purpose: this is called from a method on
     * the same bean, so the annotation would not be honoured by the proxy and
     * would only read as a guarantee that isn't there. The single save is atomic
     * on its own, and the two lookups before it are reads.
     */
    private TailoredResume persist(Long userId, Long baseResumeId, TailorRequest request,
                                   ResumeData result, Instant billingPeriod) {
        UserAccount user = users.findById(userId)
                .orElseThrow(() -> ApiException.unauthorized("This account no longer exists."));
        BaseResume base = baseResumeId == null ? null : resumes.getOwned(userId, baseResumeId);

        return tailored.save(TailoredResume.of(
                user,
                base,
                JobTitles.guess(request.jobDescription()),
                request.jobDescription().strip(),
                codec.write(result),
                result.matchScore(),
                tailorClient.modelName(),
                billingPeriod));
    }

    private static TailoredResumeDetail detail(TailoredResume entity, ResumeData resume) {
        return new TailoredResumeDetail(
                entity.getId(),
                entity.getJobTitle(),
                entity.getMatchScore(),
                entity.getModelName(),
                entity.getCreatedAt(),
                resume);
    }
}
