package com.tailorresume.billing;

import com.tailorresume.billing.BillingDtos.PlanOption;
import com.tailorresume.billing.BillingDtos.SubscribeRequest;
import com.tailorresume.billing.BillingDtos.SubscriptionResponse;
import com.tailorresume.security.AuthenticatedUser;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/billing")
public class BillingController {

    private final SubscriptionService subscriptions;
    private final QuotaService quotas;

    public BillingController(SubscriptionService subscriptions, QuotaService quotas) {
        this.subscriptions = subscriptions;
        this.quotas = quotas;
    }

    /** Public, so pricing can be shown before signup. */
    @GetMapping("/plans")
    public List<PlanOption> plans() {
        return List.of(
                new PlanOption(Plan.FREE, Plan.FREE.displayName(),
                        quotas.monthlyLimitFor(Plan.FREE), "Free",
                        List.of(
                                quotas.monthlyLimitFor(Plan.FREE) + " tailored resumes per month",
                                "ATS-clean PDF and DOCX download",
                                "Keyword gap report")),
                new PlanOption(Plan.PRO, Plan.PRO.displayName(),
                        quotas.monthlyLimitFor(Plan.PRO), "Coming soon",
                        List.of(
                                quotas.monthlyLimitFor(Plan.PRO) + " tailored resumes per month",
                                "Unlimited saved base resumes",
                                "Full tailoring history")));
    }

    @GetMapping("/subscription")
    public SubscriptionResponse current(@AuthenticationPrincipal AuthenticatedUser user) {
        return SubscriptionResponse.from(quotas.statusFor(user.id()));
    }

    /**
     * Moves the account onto the requested plan.
     *
     * <p>No money moves yet — see {@link SubscriptionService#subscribe}. When a
     * gateway is added this endpoint returns a checkout URL instead, and the
     * plan change happens in the webhook.
     */
    @PostMapping("/subscribe")
    public SubscriptionResponse subscribe(@AuthenticationPrincipal AuthenticatedUser user,
                                          @Valid @RequestBody SubscribeRequest request) {
        subscriptions.subscribe(user.id(), request.plan());
        return SubscriptionResponse.from(quotas.statusFor(user.id()));
    }

    @PostMapping("/cancel")
    public SubscriptionResponse cancel(@AuthenticationPrincipal AuthenticatedUser user) {
        subscriptions.cancel(user.id());
        return SubscriptionResponse.from(quotas.statusFor(user.id()));
    }
}
