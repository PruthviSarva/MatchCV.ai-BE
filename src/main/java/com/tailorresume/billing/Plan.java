package com.tailorresume.billing;

/**
 * Subscription tier, stored as a string in {@code subscriptions.plan} and
 * mirrored by a check constraint in the migration.
 *
 * <p>The monthly allowance is not held here — it is configuration
 * ({@code app.quota.*}) so pricing can change without a code change.
 */
public enum Plan {
    FREE,
    PRO;

    public String displayName() {
        return this == PRO ? "Pro" : "Free";
    }
}
