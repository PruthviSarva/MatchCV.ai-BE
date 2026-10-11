package com.tailorresume.admin;

import com.tailorresume.user.UserAccount;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.time.Instant;

/**
 * The admin overview's nine counts, in one round trip.
 *
 * <p>Asked for individually they are nine derived queries, and against a
 * managed database a few hundred miles away the latency -- not the counting --
 * is what the page waits on. Nine sequential trips is several seconds for work
 * that is milliseconds once the connection is warm. Scalar subqueries let the
 * whole lot travel together.
 *
 * <p>Native SQL rather than JPQL because JPQL has no way to select a list of
 * independent scalar subqueries with no root entity. Everything in it is
 * ordinary standard SQL, which both PostgreSQL and the H2 the tests run on
 * accept unchanged -- and {@code AdminConsoleIntegrationTest} asserts the
 * numbers it produces, so a dialect difference would fail the build rather
 * than quietly report nonsense.
 */
public interface OverviewStatsRepository extends Repository<UserAccount, Long> {

    interface OverviewCounts {
        long getTotalUsers();

        long getDisabledUsers();

        long getAdmins();

        long getNewUsers();

        long getTotalSubscriptions();

        long getProPlans();

        long getTotalResumes();

        long getTotalTailorings();

        long getRecentTailorings();

        /** Null when nothing has been tailored; there is no average of nothing. */
        Double getAverageMatch();
    }

    @Query(value = """
            SELECT
              (SELECT count(*) FROM users)                                    AS totalUsers,
              (SELECT count(*) FROM users WHERE active = FALSE)               AS disabledUsers,
              (SELECT count(*) FROM users WHERE role = 'ADMIN')               AS admins,
              (SELECT count(*) FROM users WHERE created_at > :since)          AS newUsers,
              (SELECT count(*) FROM subscriptions)                            AS totalSubscriptions,
              (SELECT count(*) FROM subscriptions WHERE plan = 'PRO')         AS proPlans,
              (SELECT count(*) FROM base_resumes)                             AS totalResumes,
              (SELECT count(*) FROM tailored_resumes)                         AS totalTailorings,
              (SELECT count(*) FROM tailored_resumes WHERE created_at > :since)
                                                                              AS recentTailorings,
              (SELECT avg(match_score) FROM tailored_resumes)                 AS averageMatch
            """, nativeQuery = true)
    OverviewCounts countEverything(@Param("since") Instant since);
}
