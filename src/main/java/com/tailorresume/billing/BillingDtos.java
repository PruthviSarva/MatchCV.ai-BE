package com.tailorresume.billing;

import jakarta.validation.constraints.NotNull;

import java.util.List;

public final class BillingDtos {

    private BillingDtos() {
    }

    /** One entry in the public plan catalogue. */
    public record PlanOption(
            Plan plan,
            String name,
            int monthlyTailorings,
            String priceLabel,
            List<String> features) {
    }

    public record SubscribeRequest(@NotNull(message = "Plan is required") Plan plan) {
    }

    /** The account's current plan plus where it stands against the allowance. */
    public record SubscriptionResponse(
            Plan plan,
            SubscriptionStatus status,
            int monthlyTailorings,
            long used,
            long remaining,
            java.time.Instant periodStart,
            java.time.Instant periodEnd,
            boolean checkoutStubbed) {

        public static SubscriptionResponse from(QuotaService.QuotaStatus quota) {
            return new SubscriptionResponse(
                    quota.plan(),
                    quota.status(),
                    quota.monthlyLimit(),
                    quota.used(),
                    quota.remaining(),
                    quota.periodStart(),
                    quota.periodEnd(),
                    // Flagged in the payload so the frontend can label the
                    // upgrade button honestly until a gateway is wired in.
                    true);
        }
    }
}
