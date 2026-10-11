package com.tailorresume.billing;

import java.time.Instant;

/**
 * How many tailorings a user has spent in one billing period.
 *
 * <p>Matched on the period the tailoring was stamped with, not on a timestamp
 * range. A range comparison sounds equivalent and is not: the system clock on
 * some platforms does not advance between two consecutive reads, so a tailoring
 * run moments before an upgrade can land on the exact instant the new period
 * starts and be counted against the plan the user just paid for.
 *
 * <p>An interface rather than a direct repository call so billing does not
 * depend on the tailor package while the tailor package depends on billing for
 * its quota check.
 */
public interface TailoringUsage {

    long countForPeriod(Long userId, Instant periodStart);
}
