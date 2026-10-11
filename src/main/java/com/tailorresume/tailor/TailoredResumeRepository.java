package com.tailorresume.tailor;

import com.tailorresume.common.OwnerCount;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface TailoredResumeRepository extends JpaRepository<TailoredResume, Long> {

    List<TailoredResume> findByUserIdOrderByCreatedAtDesc(Long userId);

    /** Owner-scoped: an id alone must never resolve to someone else's resume. */
    Optional<TailoredResume> findByIdAndUserId(Long id, Long userId);

    /**
     * Backs the quota count. An equality match on the stamped period rather
     * than a {@code createdAt >= periodStart} range -- see
     * {@link com.tailorresume.billing.TailoringUsage} for why that distinction
     * is load-bearing.
     */
    long countByUserIdAndBillingPeriodStart(Long userId, Instant periodStart);

    /* ---------------------------------------------------------- admin reads */

    long countByUserId(Long userId);

    long countByCreatedAtAfter(Instant cutoff);

    /**
     * The admin activity feed. Not owner-scoped, unlike everything above --
     * that is the point of the console, and the only thing that reaches it is
     * the ROLE_ADMIN rule in SecurityConfig.
     */
    @Query("select t from TailoredResume t join fetch t.user order by t.createdAt desc")
    List<TailoredResume> findRecentWithUser(Pageable pageable);

    @Query("select t.user.id as userId, count(t) as total from TailoredResume t "
            + "where t.user.id in :userIds group by t.user.id")
    List<OwnerCount> countByUserIds(@Param("userIds") Collection<Long> userIds);

    /** Null when nothing has been tailored yet; the caller decides what to show. */
    @Query("select avg(t.matchScore) from TailoredResume t")
    Double averageMatchScore();
}
