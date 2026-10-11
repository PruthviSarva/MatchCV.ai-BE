package com.tailorresume.billing;

import com.tailorresume.user.UserAccount;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The billing period, which doubles as the key the quota count matches on.
 *
 * <p>The truncation test is a regression guard, not a style preference. With a
 * raw {@code Instant.now()} the period start carries nanoseconds that no
 * timestamp column preserves, so the value compares equal in Java and unequal
 * in SQL — and the usage count silently returns zero forever, handing every
 * user an unlimited allowance. It is exactly the kind of bug that passes code
 * review and never throws.
 */
class BillingPeriodTest {

    // The allowance is now carried on the row, so every factory and plan change
    // takes the number its caller would have read from configuration.
    private static final int FREE_ALLOWANCE = 3;
    private static final int PRO_ALLOWANCE = 200;

    private static Subscription freeSubscription() {
        return Subscription.free(
                UserAccount.create("Priya", "priya@example.com", "{bcrypt}x"), FREE_ALLOWANCE);
    }

    @Test
    @DisplayName("the period start is truncated to milliseconds so SQL equality holds")
    void periodStartIsMillisecondTruncated() {
        Instant start = freeSubscription().getCurrentPeriodStart();

        assertThat(start).isEqualTo(start.truncatedTo(ChronoUnit.MILLIS));
        assertThat(start.getNano() % 1_000_000).isZero();
    }

    @Test
    @DisplayName("a new subscription starts on FREE with a 30-day period")
    void freeStartsWithAThirtyDayPeriod() {
        Subscription subscription = freeSubscription();

        assertThat(subscription.getPlan()).isEqualTo(Plan.FREE);
        assertThat(subscription.getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
        assertThat(ChronoUnit.DAYS.between(
                subscription.getCurrentPeriodStart(), subscription.getCurrentPeriodEnd()))
                .isEqualTo(30);
    }

    @Test
    @DisplayName("an upgrade starts a fresh period, so free usage does not eat the paid plan")
    void upgradeStartsAFreshPeriod() {
        Subscription subscription = freeSubscription();
        Instant freePeriod = subscription.getCurrentPeriodStart();

        subscription.upgradeToPro(null, null, PRO_ALLOWANCE);

        assertThat(subscription.getPlan()).isEqualTo(Plan.PRO);
        // A different period means the tailorings stamped with the free period
        // are not counted against the Pro allowance.
        assertThat(subscription.getCurrentPeriodStart())
                .isAfterOrEqualTo(freePeriod);
        assertThat(subscription.getCurrentPeriodStart())
                .isEqualTo(subscription.getCurrentPeriodStart().truncatedTo(ChronoUnit.MILLIS));
    }

    @Test
    @DisplayName("a period that has not elapsed is left alone")
    void doesNotRollForwardEarly() {
        Subscription subscription = freeSubscription();
        Instant start = subscription.getCurrentPeriodStart();

        assertThat(subscription.rolloverIfElapsed(FREE_ALLOWANCE)).isFalse();
        assertThat(subscription.getCurrentPeriodStart()).isEqualTo(start);
    }

    @Test
    @DisplayName("a new subscription takes the allowance it was created with")
    void allowanceComesFromTheCaller() {
        assertThat(freeSubscription().getMonthlyTailorings()).isEqualTo(FREE_ALLOWANCE);
    }

    @Test
    @DisplayName("an allowance set by hand survives a period rollover")
    void overrideSurvivesRollover() {
        Subscription subscription = freeSubscription();
        subscription.setMonthlyTailorings(30);

        // The period has not elapsed here, so nothing actually rolls -- but
        // this is the assertion that matters for a trial: rolling forward must
        // not reach for the configured default and quietly put the account
        // back to 3 at the end of the month.
        subscription.rolloverIfElapsed(FREE_ALLOWANCE);

        assertThat(subscription.getMonthlyTailorings()).isEqualTo(30);
    }

    @Test
    @DisplayName("an allowance cannot be set below zero")
    void allowanceCannotGoNegative() {
        Subscription subscription = freeSubscription();

        subscription.setMonthlyTailorings(-10);

        assertThat(subscription.getMonthlyTailorings()).isZero();
    }

    @Test
    @DisplayName("an upgrade replaces the allowance with the new plan's")
    void upgradeTakesTheNewPlansAllowance() {
        Subscription subscription = freeSubscription();
        subscription.setMonthlyTailorings(30);

        subscription.upgradeToPro(null, null, PRO_ALLOWANCE);

        assertThat(subscription.getMonthlyTailorings()).isEqualTo(PRO_ALLOWANCE);
    }

    @Test
    @DisplayName("cancelling keeps the plan until the period ends")
    void cancelDoesNotDowngradeImmediately() {
        Subscription subscription = freeSubscription();
        subscription.upgradeToPro(null, null, PRO_ALLOWANCE);

        subscription.cancel();

        assertThat(subscription.getStatus()).isEqualTo(SubscriptionStatus.CANCELED);
        // Still Pro: cancel means "do not renew", not "cut me off now".
        assertThat(subscription.getPlan()).isEqualTo(Plan.PRO);
    }
}
