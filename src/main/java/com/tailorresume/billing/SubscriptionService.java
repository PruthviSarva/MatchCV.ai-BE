package com.tailorresume.billing;

import com.tailorresume.common.ApiException;
import com.tailorresume.config.AppProperties;
import com.tailorresume.user.UserAccount;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reads and changes the subscription row.
 *
 * <p>{@link #subscribe} and {@link #cancel} are the seams a payment gateway
 * will plug into: today they flip the plan directly, and when a gateway is
 * wired in the same methods will be driven from its webhook instead of from
 * the user's own request. Nothing else in the codebase needs to change.
 *
 * <p>This class is also where the configured per-plan allowance meets the
 * stored one. Configuration decides what a plan starts with; the row decides
 * what the account actually gets. Every method that creates a subscription or
 * moves it between plans copies the configured number onto the row, and
 * nothing else touches it -- so an allowance raised by hand stays raised.
 */
@Service
public class SubscriptionService {

    private final SubscriptionRepository subscriptions;
    private final AppProperties.Quota defaults;

    public SubscriptionService(SubscriptionRepository subscriptions, AppProperties properties) {
        this.subscriptions = subscriptions;
        this.defaults = properties.quota();
    }

    /** What a brand-new subscription on this plan is given. */
    public int defaultAllowanceFor(Plan plan) {
        return plan == Plan.PRO ? defaults.proMonthly() : defaults.freeMonthly();
    }

    @Transactional
    public Subscription startFreePlan(UserAccount user) {
        return subscriptions.save(Subscription.free(user, defaultAllowanceFor(Plan.FREE)));
    }

    /**
     * The user's subscription, with an elapsed period rolled forward first so
     * every caller sees a current window.
     */
    @Transactional
    public Subscription currentFor(Long userId) {
        Subscription subscription = subscriptions.findByUserId(userId)
                .orElseThrow(() -> ApiException.notFound("No subscription for this account."));

        if (subscription.rolloverIfElapsed(defaultAllowanceFor(Plan.FREE))) {
            subscriptions.save(subscription);
        }
        return subscription;
    }

    /**
     * Moves the account onto PRO.
     *
     * <p>Checkout is stubbed: there is no gateway call and no money moves, so
     * the provider ids are null. When a gateway is added, this method takes its
     * customer and subscription ids and is invoked from the webhook -- the
     * client must never be the thing that says "I paid".
     */
    @Transactional
    public Subscription subscribe(Long userId, Plan plan) {
        if (plan == Plan.FREE) {
            throw ApiException.badRequest("Use cancel to move back to the free plan.");
        }

        Subscription subscription = currentFor(userId);
        if (subscription.getPlan() == Plan.PRO
                && subscription.getStatus() == SubscriptionStatus.ACTIVE) {
            throw ApiException.conflict("You are already on the Pro plan.");
        }

        subscription.upgradeToPro(null, null, defaultAllowanceFor(Plan.PRO));
        return subscriptions.save(subscription);
    }

    /** Cancel at period end. PRO access continues until the period actually ends. */
    @Transactional
    public Subscription cancel(Long userId) {
        Subscription subscription = currentFor(userId);
        if (subscription.getPlan() == Plan.FREE) {
            throw ApiException.badRequest("You are on the free plan; there is nothing to cancel.");
        }
        if (subscription.getStatus() == SubscriptionStatus.CANCELED) {
            throw ApiException.conflict("This subscription is already set to cancel.");
        }

        subscription.cancel();
        return subscriptions.save(subscription);
    }

    /**
     * Sets the plan outright. The admin console's lever, not the user's.
     *
     * <p>{@link #subscribe} and {@link #cancel} model what a subscriber is
     * allowed to do to their own account, and both refuse a no-op with a 409
     * so the UI can say something useful. An administrator setting an account
     * to the plan it already has is not an error worth interrupting them over,
     * so this returns quietly instead -- and returning quietly is also what
     * keeps it safe, since changing plan re-starts the billing period and a
     * repeated "set to PRO" would otherwise hand out a fresh allowance on
     * every click.
     */
    @Transactional
    public Subscription setPlan(Long userId, Plan plan) {
        Subscription subscription = currentFor(userId);
        if (subscription.getPlan() == plan
                && subscription.getStatus() == SubscriptionStatus.ACTIVE) {
            return subscription;
        }

        if (plan == Plan.PRO) {
            subscription.upgradeToPro(null, null, defaultAllowanceFor(Plan.PRO));
        } else {
            subscription.downgradeToFree(defaultAllowanceFor(Plan.FREE));
        }
        return subscriptions.save(subscription);
    }

    /**
     * Gives one account a different allowance from everybody else on its plan.
     *
     * <p>This is what makes a trial possible without a deployment: raise the
     * number for the people being asked to test and leave the plan default
     * where it is. The same column can be edited directly in the database --
     * {@code UPDATE subscriptions SET monthly_tailorings = 30 WHERE user_id =
     * ...} is the whole operation, and it takes effect on the next request
     * because nothing caches it.
     */
    @Transactional
    public Subscription setMonthlyAllowance(Long userId, int allowance) {
        if (allowance < 0) {
            throw ApiException.badRequest("An allowance cannot be negative.");
        }
        Subscription subscription = currentFor(userId);
        subscription.setMonthlyTailorings(allowance);
        return subscriptions.save(subscription);
    }
}
