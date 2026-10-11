package com.tailorresume.admin;

import com.tailorresume.admin.AdminDtos.ActivityEntry;
import com.tailorresume.admin.AdminDtos.AdminUserDetail;
import com.tailorresume.admin.AdminDtos.AdminUserSummary;
import com.tailorresume.admin.AdminDtos.CreateUserRequest;
import com.tailorresume.admin.AdminDtos.OverviewResponse;
import com.tailorresume.admin.AdminDtos.UserPage;
import com.tailorresume.auth.RefreshTokenRepository;
import com.tailorresume.billing.BillingDtos.SubscriptionResponse;
import com.tailorresume.billing.Plan;
import com.tailorresume.billing.QuotaService;
import com.tailorresume.billing.Subscription;
import com.tailorresume.billing.SubscriptionRepository;
import com.tailorresume.billing.SubscriptionService;
import com.tailorresume.common.ApiException;
import com.tailorresume.common.OwnerCount;
import com.tailorresume.resume.BaseResumeRepository;
import com.tailorresume.resume.ResumeDtos.BaseResumeSummary;
import com.tailorresume.tailor.TailorDtos.TailoredResumeSummary;
import com.tailorresume.tailor.TailoredResume;
import com.tailorresume.tailor.TailoredResumeRepository;
import com.tailorresume.user.Role;
import com.tailorresume.user.UserAccount;
import com.tailorresume.user.UserRepository;
import com.tailorresume.user.UserSpecifications;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Everything the admin console can read and change.
 *
 * <p>Two rules run through all of it, and both exist to stop the console from
 * being the thing that locks everyone out of the console:
 *
 * <ul>
 *   <li>An administrator cannot disable or demote <em>themselves</em>. That
 *       mistake is one misread row away, and it ends the very session that
 *       would have been used to undo it.
 *   <li>The last enabled administrator cannot be disabled or demoted by
 *       anybody. Two admins who each demote the other, or one who disables the
 *       only colleague before logging out, would leave the application with no
 *       way back in short of editing the database by hand.
 * </ul>
 *
 * <p>Authorization itself is not here. {@code SecurityConfig} gates the whole
 * {@code /api/admin/**} prefix on ROLE_ADMIN, and the role behind that is read
 * from the database on every request -- so the guards below are about sane
 * outcomes, not about keeping non-admins out.
 */
@Service
public class AdminService {

    private static final Duration RECENT_WINDOW = Duration.ofDays(7);
    private static final int ACTIVITY_FEED_SIZE = 12;
    private static final int MAX_PAGE_SIZE = 100;

    private final UserRepository users;
    private final OverviewStatsRepository stats;
    private final BaseResumeRepository resumes;
    private final TailoredResumeRepository tailorings;
    private final SubscriptionRepository subscriptionRows;
    private final SubscriptionService subscriptions;
    private final QuotaService quotas;
    private final RefreshTokenRepository refreshTokens;
    private final PasswordEncoder passwordEncoder;

    public AdminService(UserRepository users,
                        OverviewStatsRepository stats,
                        BaseResumeRepository resumes,
                        TailoredResumeRepository tailorings,
                        SubscriptionRepository subscriptionRows,
                        SubscriptionService subscriptions,
                        QuotaService quotas,
                        RefreshTokenRepository refreshTokens,
                        PasswordEncoder passwordEncoder) {
        this.users = users;
        this.stats = stats;
        this.resumes = resumes;
        this.tailorings = tailorings;
        this.subscriptionRows = subscriptionRows;
        this.subscriptions = subscriptions;
        this.quotas = quotas;
        this.refreshTokens = refreshTokens;
        this.passwordEncoder = passwordEncoder;
    }

    /* -------------------------------------------------------------- reading */

    /**
     * The console's front page.
     *
     * <p>Assembled from four round trips rather than a dozen. Against a
     * managed database the trips -- not the work in them -- are what the page
     * waits on, so the nine scalar counts travel together (see
     * {@link OverviewStatsRepository}), the recent-signup list is fetched once
     * and used both for the "newest accounts" strip and for the activity feed,
     * and the two counts that are complements of others are subtracted rather
     * than asked for.
     *
     * <p>Those subtractions are exact, not approximations: {@code active} is a
     * NOT NULL boolean, and a check constraint limits {@code plan} to FREE or
     * PRO, so neither complement can drift.
     */
    @Transactional(readOnly = true)
    public OverviewResponse overview() {
        Instant since = Instant.now().minus(RECENT_WINDOW);

        OverviewStatsRepository.OverviewCounts counts = stats.countEverything(since);
        Double average = counts.getAverageMatch();
        List<UserAccount> newest = users.findTop8ByOrderByCreatedAtDesc();

        return new OverviewResponse(
                counts.getTotalUsers(),
                counts.getTotalUsers() - counts.getDisabledUsers(),
                counts.getDisabledUsers(),
                counts.getAdmins(),
                counts.getNewUsers(),
                counts.getTotalSubscriptions() - counts.getProPlans(),
                counts.getProPlans(),
                counts.getTotalResumes(),
                counts.getTotalTailorings(),
                counts.getRecentTailorings(),
                average == null ? null : (int) Math.round(average),
                summarize(newest),
                activityFrom(newest));
    }

    /**
     * Signups and tailorings, interleaved, newest first.
     *
     * <p>Built by merging two queries rather than by a UNION or an events
     * table. There are two kinds of event and a dozen rows on screen; an
     * append-only event log is the right answer the moment there is a third
     * kind, or a need to page back through history, and premature until then.
     */
    @Transactional(readOnly = true)
    public List<ActivityEntry> activity() {
        return activityFrom(users.findTop8ByOrderByCreatedAtDesc());
    }

    /**
     * The feed, given the recent signups the caller has already fetched.
     *
     * <p>{@link #overview()} needs that same list for its "newest accounts"
     * strip, and asking for it twice in one request is a round trip spent on
     * rows already in hand.
     */
    private List<ActivityEntry> activityFrom(List<UserAccount> recentUsers) {
        List<ActivityEntry> entries = new ArrayList<>();

        for (UserAccount user : recentUsers) {
            entries.add(new ActivityEntry(
                    "SIGNUP", user.getId(), user.getName(), user.getEmail(),
                    "Created an account", null, user.getCreatedAt()));
        }

        // join fetch on the owner: the feed names the user on every line, and
        // a lazy association would be one query per row.
        for (TailoredResume tailored : tailorings.findRecentWithUser(
                PageRequest.of(0, ACTIVITY_FEED_SIZE))) {
            UserAccount owner = tailored.getUser();
            entries.add(new ActivityEntry(
                    "TAILORING", owner.getId(), owner.getName(), owner.getEmail(),
                    tailored.getJobTitle() == null ? "Tailored a resume" : tailored.getJobTitle(),
                    tailored.getMatchScore(), tailored.getCreatedAt()));
        }

        entries.sort(Comparator.comparing(ActivityEntry::at).reversed());
        return entries.size() > ACTIVITY_FEED_SIZE
                ? List.copyOf(entries.subList(0, ACTIVITY_FEED_SIZE))
                : entries;
    }

    @Transactional(readOnly = true)
    public UserPage search(String query, Boolean active, Role role, int page, int size) {
        int safeSize = Math.clamp(size, 1, MAX_PAGE_SIZE);
        PageRequest pageable = PageRequest.of(
                Math.max(page, 0), safeSize, Sort.by(Sort.Direction.DESC, "createdAt"));

        Page<UserAccount> found = users.findAll(
                allPresent(
                        UserSpecifications.matching(query),
                        UserSpecifications.active(active),
                        UserSpecifications.role(role)),
                pageable);
        return new UserPage(
                summarize(found.getContent()),
                found.getNumber(),
                found.getSize(),
                found.getTotalElements(),
                found.getTotalPages());
    }

    @Transactional(readOnly = true)
    public AdminUserDetail detail(Long userId) {
        UserAccount user = require(userId);

        return new AdminUserDetail(
                summarize(List.of(user)).getFirst(),
                SubscriptionResponse.from(quotas.statusFor(userId)),
                resumes.findByUserIdOrderByCreatedAtDesc(userId).stream()
                        .map(BaseResumeSummary::from)
                        .toList(),
                tailorings.findByUserIdOrderByCreatedAtDesc(userId).stream()
                        .map(TailoredResumeSummary::from)
                        .toList());
    }

    /* -------------------------------------------------------------- writing */

    @Transactional
    public AdminUserDetail create(CreateUserRequest request) {
        if (users.existsByEmail(request.email())) {
            throw ApiException.conflict("That email already has an account.");
        }

        UserAccount user = UserAccount.createWithRole(
                request.name().trim(),
                request.email(),
                passwordEncoder.encode(request.password()),
                request.role());
        users.save(user);

        // Same order as signup: the subscription row exists before anything can
        // read the account, so no code path has to cope with a user that has no
        // plan.
        subscriptions.startFreePlan(user);
        if (request.plan() == Plan.PRO) {
            subscriptions.setPlan(user.getId(), Plan.PRO);
        }

        return detail(user.getId());
    }

    /**
     * Enables or disables an account.
     *
     * <p>Disabling revokes the refresh family as well as flipping the flag.
     * The flag alone already stops the next request -- the authentication
     * filter re-reads it -- but the refresh token would still be sitting in
     * that browser, and leaving it live means the account springs back with a
     * session nobody expected to still exist the moment it is re-enabled.
     */
    @Transactional
    public AdminUserDetail setActive(Long actingAdminId, Long userId, boolean active) {
        UserAccount user = require(userId);

        if (!active) {
            if (user.getId().equals(actingAdminId)) {
                throw ApiException.badRequest(
                        "You cannot disable your own account. Ask another administrator.");
            }
            requireAnotherAdminRemains(user, "disable");
        }

        if (active) {
            user.activate();
        } else {
            user.deactivate();
            refreshTokens.revokeAllForUser(userId, Instant.now());
        }
        users.save(user);

        return detail(userId);
    }

    @Transactional
    public AdminUserDetail setRole(Long actingAdminId, Long userId, Role role) {
        UserAccount user = require(userId);

        // Only a change that takes administrator access away can lock anyone
        // out, so promotions skip the guards entirely.
        if (user.getRole() != role && role != Role.ADMIN) {
            if (user.getId().equals(actingAdminId)) {
                throw ApiException.badRequest(
                        "You cannot remove your own administrator access.");
            }
            requireAnotherAdminRemains(user, "demote");
        }

        user.changeRole(role);
        users.save(user);
        return detail(userId);
    }

    @Transactional
    public AdminUserDetail setPlan(Long userId, Plan plan) {
        require(userId);
        subscriptions.setPlan(userId, plan);
        return detail(userId);
    }

    /**
     * Gives one account a different monthly allowance from the rest of its
     * plan.
     *
     * <p>The lever for running a trial without a deployment: raise the number
     * for the people being asked to test, leave the plan default alone. It
     * does not restart the billing period, so an account halfway through a
     * period immediately has more room rather than a clean slate.
     */
    @Transactional
    public AdminUserDetail setAllowance(Long userId, int monthlyTailorings) {
        require(userId);
        subscriptions.setMonthlyAllowance(userId, monthlyTailorings);
        return detail(userId);
    }

    /* --------------------------------------------------------------- shared */

    /**
     * Combines the filters that are actually present.
     *
     * <p>{@link UserSpecifications} returns null for a filter the caller did
     * not supply, and {@code Specification.allOf} rejects a null element rather
     * than ignoring it -- so an unfiltered "show me every account" would throw
     * on the way in. Dropping the nulls here keeps that decision in one place
     * instead of making each call site pre-check its own arguments. An empty
     * list is a specification that matches everything, which is exactly what an
     * unfiltered search means.
     */
    @SafeVarargs
    private static Specification<UserAccount> allPresent(
            Specification<UserAccount>... parts) {
        return Specification.allOf(
                Arrays.stream(parts).filter(Objects::nonNull).toList());
    }

    private UserAccount require(Long userId) {
        return users.findById(userId)
                .orElseThrow(() -> ApiException.notFound("No account with that id."));
    }

    /**
     * Refuses the change when {@code user} is the only enabled administrator.
     *
     * <p>Counts enabled admins other than this one, rather than admins in
     * total: an application whose only remaining admin account is disabled has
     * no way back in either.
     */
    private void requireAnotherAdminRemains(UserAccount user, String verb) {
        if (!user.isAdmin()) {
            return;
        }
        boolean anotherRemains = users.findAll(allPresent(
                        UserSpecifications.role(Role.ADMIN),
                        UserSpecifications.active(true)))
                .stream()
                .anyMatch(candidate -> !candidate.getId().equals(user.getId()));

        if (!anotherRemains) {
            throw ApiException.badRequest(
                    "This is the only active administrator. Promote someone else before you "
                            + verb + " this account.");
        }
    }

    /**
     * Turns accounts into table rows, fetching the per-owner totals for the
     * whole page in two grouped queries rather than two per row.
     */
    private List<AdminUserSummary> summarize(List<UserAccount> accounts) {
        if (accounts.isEmpty()) {
            return List.of();
        }
        List<Long> ids = accounts.stream().map(UserAccount::getId).toList();

        Map<Long, Long> resumeCounts = toMap(resumes.countByUserIds(ids));
        Map<Long, Long> tailoringCounts = toMap(tailorings.countByUserIds(ids));

        // One query for the whole page, not one per row. Against a managed
        // database a few hundred miles away each round trip costs hundreds of
        // milliseconds, so a twenty-row page was twenty avoidable round trips.
        Map<Long, Subscription> plans = new HashMap<>();
        for (Subscription row : subscriptionRows.findAllForUsers(ids)) {
            plans.put(row.getUser().getId(), row);
        }

        return accounts.stream()
                .map(user -> {
                    Subscription plan = plans.get(user.getId());
                    return new AdminUserSummary(
                            user.getId(),
                            user.getName(),
                            user.getEmail(),
                            user.getRole(),
                            user.isActive(),
                            plan == null ? null : plan.getPlan(),
                            plan == null ? null : plan.getStatus(),
                            resumeCounts.getOrDefault(user.getId(), 0L),
                            tailoringCounts.getOrDefault(user.getId(), 0L),
                            user.getCreatedAt(),
                            user.getLastLoginAt());
                })
                .toList();
    }

    private static Map<Long, Long> toMap(List<OwnerCount> counts) {
        return counts.stream().collect(Collectors.toMap(
                OwnerCount::getUserId, OwnerCount::getTotal, Long::sum));
    }
}
