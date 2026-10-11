package com.tailorresume.billing;

/**
 * Lifecycle of a subscription row.
 *
 * <p>{@link #CANCELED} means "will not renew" rather than "cut off now" — the
 * user keeps PRO until {@code currentPeriodEnd}, which is what every real
 * gateway's cancel-at-period-end does.
 */
public enum SubscriptionStatus {
    ACTIVE,
    CANCELED,
    EXPIRED
}
