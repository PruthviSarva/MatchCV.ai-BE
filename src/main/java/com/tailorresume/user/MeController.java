package com.tailorresume.user;

import com.tailorresume.billing.BillingDtos.SubscriptionResponse;
import com.tailorresume.billing.QuotaService;
import com.tailorresume.common.ApiException;
import com.tailorresume.security.AuthenticatedUser;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The one call the frontend makes on load: who am I, and what am I allowed to
 * do right now. Bundling the plan in avoids a second round trip on every
 * screen that has to decide whether to show the upgrade prompt.
 */
@RestController
@RequestMapping("/api/me")
public class MeController {

    private final UserRepository users;
    private final QuotaService quotas;

    public MeController(UserRepository users, QuotaService quotas) {
        this.users = users;
        this.quotas = quotas;
    }

    public record MeResponse(UserResponse user, SubscriptionResponse subscription) {
    }

    @GetMapping
    public MeResponse me(@AuthenticationPrincipal AuthenticatedUser principal) {
        UserAccount user = users.findById(principal.id())
                .orElseThrow(() -> ApiException.unauthorized("This account no longer exists."));

        return new MeResponse(
                UserResponse.from(user),
                SubscriptionResponse.from(quotas.statusFor(user.getId())));
    }
}
