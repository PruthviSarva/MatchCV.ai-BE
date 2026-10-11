package com.tailorresume.billing;

import com.tailorresume.user.UserAccount;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * One row per user: the plan they are on and the billing period that plan is
 * counted against.
 *
 * <p>The period is explicit rather than derived from the calendar month,
 * because a plan bought on the 20th should give a full month — and because a
 * real gateway will hand us its own period boundaries to store here.
 *
 * <p>{@code providerCustomerId} / {@code providerSubscriptionId} are the seams
 * for a payment gateway. They stay null while checkout is stubbed.
 */
@Entity
@Table(name = "subscriptions")
public class Subscription {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private UserAccount user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Plan plan = Plan.FREE;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private SubscriptionStatus status = SubscriptionStatus.ACTIVE;

    /**
     * How many tailorings this account gets per period.
     *
     * <p>Stored on the row rather than read from configuration at every check.
     * Configuration still supplies the number a plan <em>starts</em> with, but
     * once the subscription exists this column is the authority -- which is
     * what makes it possible to hand one tester a bigger allowance by changing
     * a single row, with no redeploy and without giving everybody else the
     * same bump.
     *
     * <p>The consequence is that editing {@code app.quota.*} no longer moves
     * accounts that already exist. That is the right way round: an allowance
     * somebody is relying on should not change because an unrelated deployment
     * shipped a different default.
     */
    @Column(name = "monthly_tailorings", nullable = false)
    private int monthlyTailorings;

    @Column(name = "current_period_start", nullable = false)
    private Instant currentPeriodStart;

    @Column(name = "current_period_end", nullable = false)
    private Instant currentPeriodEnd;

    @Column(name = "provider_customer_id", length = 128)
    private String providerCustomerId;

    @Column(name = "provider_subscription_id", length = 128)
    private String providerSubscriptionId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected Subscription() {
        // for JPA
    }

    /**
     * @param freeAllowance the configured free-plan allowance, copied onto the
     *                      row so that later changes to the configured default
     *                      leave this account alone
     */
    public static Subscription free(UserAccount user, int freeAllowance) {
        Subscription subscription = new Subscription();
        subscription.user = user;
        subscription.plan = Plan.FREE;
        subscription.status = SubscriptionStatus.ACTIVE;
        subscription.monthlyTailorings = freeAllowance;
        subscription.startPeriodAt(Instant.now());
        return subscription;
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        if (createdAt == null) {
            createdAt = now;
        }
        updatedAt = now;
    }

    @PreUpdate
    void touch() {
        this.updatedAt = Instant.now();
    }

    /** A 30-day window rather than a calendar month, so every period is equal. */
    private void startPeriodAt(Instant start) {
        // Truncated to milliseconds because the period start is an identifier,
        // not just a timestamp: each tailoring is stamped with it and the quota
        // count matches on equality. Instant.now() carries nanoseconds that no
        // timestamp column preserves, so an untruncated value compares equal in
        // Java and unequal in SQL — and the quota silently never counts
        // anything. Milliseconds survive every database we would plausibly use.
        Instant exact = start.truncatedTo(ChronoUnit.MILLIS);
        this.currentPeriodStart = exact;
        this.currentPeriodEnd = exact.plus(30, ChronoUnit.DAYS);
    }

    /**
     * Rolls the period forward when the current one has lapsed. Called on every
     * quota read, so usage resets without a scheduled job.
     */
    public boolean rolloverIfElapsed(int freeAllowance) {
        if (Instant.now().isBefore(currentPeriodEnd)) {
            return false;
        }
        // A canceled PRO plan drops to FREE once its paid period actually ends,
        // and takes the free allowance with it. Note that a plain rollover --
        // the common case -- leaves monthlyTailorings exactly as it is, so an
        // allowance set by hand for testing survives every period boundary.
        if (status == SubscriptionStatus.CANCELED) {
            plan = Plan.FREE;
            status = SubscriptionStatus.ACTIVE;
            monthlyTailorings = freeAllowance;
        }
        startPeriodAt(Instant.now());
        return true;
    }

    public void upgradeToPro(String providerCustomerId, String providerSubscriptionId,
                             int proAllowance) {
        this.plan = Plan.PRO;
        this.status = SubscriptionStatus.ACTIVE;
        this.providerCustomerId = providerCustomerId;
        this.providerSubscriptionId = providerSubscriptionId;
        // Changing plan resets the allowance to the new plan's default, which
        // discards any hand-set override. That is intended: an override says
        // "this account, on this plan, gets more", and the plan just changed.
        this.monthlyTailorings = proAllowance;
        // A fresh period: an upgrade mid-month should not be limited by the
        // free tailorings already spent in the old window.
        startPeriodAt(Instant.now());
    }

    /** Cancel at period end — PRO access continues until {@code currentPeriodEnd}. */
    public void cancel() {
        this.status = SubscriptionStatus.CANCELED;
    }

    /**
     * Moves straight to FREE, now.
     *
     * <p>Deliberately not the same thing as {@link #cancel()}. Cancel is the
     * subscriber saying "stop billing me", so PRO keeps running until the end
     * of the period they already paid for. This is an administrator correcting
     * an account, which has to take effect immediately -- and the period
     * restarts with it, because the tailorings already spent against a PRO
     * allowance would otherwise be counted against the much smaller free one
     * and leave the account instantly over its limit.
     */
    public void downgradeToFree(int freeAllowance) {
        this.plan = Plan.FREE;
        this.status = SubscriptionStatus.ACTIVE;
        this.providerCustomerId = null;
        this.providerSubscriptionId = null;
        this.monthlyTailorings = freeAllowance;
        startPeriodAt(Instant.now());
    }

    /**
     * Overrides this account's allowance, leaving the plan and the period
     * alone.
     *
     * <p>The period deliberately does not restart. Raising a tester's
     * allowance mid-period should give them more room immediately, not hand
     * them a fresh window -- and lowering it should not quietly wipe the usage
     * already recorded against the current one.
     */
    public void setMonthlyTailorings(int allowance) {
        this.monthlyTailorings = Math.max(0, allowance);
    }

    public int getMonthlyTailorings() {
        return monthlyTailorings;
    }

    public Long getId() {
        return id;
    }

    public UserAccount getUser() {
        return user;
    }

    public Plan getPlan() {
        return plan;
    }

    public SubscriptionStatus getStatus() {
        return status;
    }

    public Instant getCurrentPeriodStart() {
        return currentPeriodStart;
    }

    public Instant getCurrentPeriodEnd() {
        return currentPeriodEnd;
    }
}
