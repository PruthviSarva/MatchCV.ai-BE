package com.tailorresume.billing;

import com.tailorresume.common.ApiException;
import com.tailorresume.config.AppProperties;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Counts tailorings against the plan's monthly allowance.
 *
 * <p>Usage is derived by counting rows in the current billing period rather
 * than kept as a counter column. That cannot drift out of sync with reality,
 * and it means a deleted tailoring genuinely frees its slot.
 */
@Service
public class QuotaService {

    private final SubscriptionService subscriptions;
    private final TailoringUsage usage;
    private final AppProperties.Quota limits;

    public QuotaService(SubscriptionService subscriptions,
                        TailoringUsage usage,
                        AppProperties properties) {
        this.subscriptions = subscriptions;
        this.usage = usage;
        this.limits = properties.quota();
    }

    /** What the frontend shows on the account screen. */
    public record QuotaStatus(
            Plan plan,
            SubscriptionStatus status,
            int monthlyLimit,
            long used,
            long remaining,
            java.time.Instant periodStart,
            java.time.Instant periodEnd) {
    }

    @Transactional
    public QuotaStatus statusFor(Long userId) {
        Subscription subscription = subscriptions.currentFor(userId);
        // Read from the row, not from configuration. The row is what an
        // administrator -- or a hand-written UPDATE during a trial -- can raise
        // for one account without moving everybody else onto the same number.
        int limit = subscription.getMonthlyTailorings();
        long used = usage.countForPeriod(userId, subscription.getCurrentPeriodStart());

        return new QuotaStatus(
                subscription.getPlan(),
                subscription.getStatus(),
                limit,
                used,
                Math.max(0, limit - used),
                subscription.getCurrentPeriodStart(),
                subscription.getCurrentPeriodEnd());
    }

    /**
     * Called before spending a tailoring.
     *
     * @return the billing period the new tailoring must be stamped with, so it
     *         counts against the period that authorized it even if the plan
     *         changes a moment later
     * @throws ApiException 402 when the allowance is exhausted
     */
    @Transactional
    public java.time.Instant claimTailoringAllowance(Long userId) {
        QuotaStatus status = statusFor(userId);
        if (status.remaining() > 0) {
            return status.periodStart();
        }

        if (status.plan() == Plan.FREE) {
            throw ApiException.paymentRequired(
                    "You have used all " + status.monthlyLimit()
                            + " free tailorings for this period. Upgrade to Pro for more.");
        }
        throw ApiException.paymentRequired(
                "You have used all " + status.monthlyLimit()
                        + " tailorings for this period. The allowance resets on "
                        + status.periodEnd() + ".");
    }

    /**
     * The advertised allowance for a plan, from configuration.
     *
     * <p>Used by the public plan catalogue, which describes what a plan offers
     * rather than what any particular account has. An existing subscription's
     * real allowance lives on its own row -- see {@link #statusFor}.
     */
    public int monthlyLimitFor(Plan plan) {
        return plan == Plan.PRO ? limits.proMonthly() : limits.freeMonthly();
    }
}
