package com.tailorresume.resume;

import com.tailorresume.common.OwnerCount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface BaseResumeRepository extends JpaRepository<BaseResume, Long> {

    List<BaseResume> findByUserIdOrderByCreatedAtDesc(Long userId);

    /**
     * Always looked up by id <em>and</em> owner. A resume id in the URL must
     * never be enough to read someone else's resume.
     */
    Optional<BaseResume> findByIdAndUserId(Long id, Long userId);

    /* ---------------------------------------------------------- admin reads */

    long countByUserId(Long userId);

    @Query("select r.user.id as userId, count(r) as total from BaseResume r "
            + "where r.user.id in :userIds group by r.user.id")
    List<OwnerCount> countByUserIds(@Param("userIds") Collection<Long> userIds);
}
