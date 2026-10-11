package com.tailorresume.admin;

import com.tailorresume.auth.Emails;
import com.tailorresume.billing.BillingDtos.SubscriptionResponse;
import com.tailorresume.billing.Plan;
import com.tailorresume.billing.SubscriptionStatus;
import com.tailorresume.resume.ResumeDtos.BaseResumeSummary;
import com.tailorresume.tailor.TailorDtos.TailoredResumeSummary;
import com.tailorresume.user.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

/**
 * Payloads for the admin console.
 *
 * <p>These are a separate set from the ones the account screens use even where
 * the fields overlap. An administrator sees things a user is not shown about
 * themselves -- the role, the disabled flag, how many resumes they are sitting
 * on -- and the two shapes should be free to move apart without one of them
 * quietly leaking a field into the other.
 */
public final class AdminDtos {

    private AdminDtos() {
    }

    /** The numbers on the console's front page. */
    public record OverviewResponse(
            long totalUsers,
            long activeUsers,
            long disabledUsers,
            long admins,
            long newUsersLast7Days,
            long freePlans,
            long proPlans,
            long totalResumes,
            long totalTailorings,
            long tailoringsLast7Days,
            /** Null until something has been tailored; there is no average of nothing. */
            Integer averageMatchScore,
            List<AdminUserSummary> recentSignups,
            List<ActivityEntry> recentActivity) {
    }

    /** One line in the activity feed. */
    public record ActivityEntry(
            String type,
            Long userId,
            String userName,
            String userEmail,
            String detail,
            Integer matchScore,
            Instant at) {
    }

    /** A row in the user table. */
    public record AdminUserSummary(
            Long id,
            String name,
            String email,
            Role role,
            boolean active,
            Plan plan,
            SubscriptionStatus planStatus,
            long resumeCount,
            long tailoringCount,
            Instant createdAt,
            Instant lastLoginAt) {
    }

    /**
     * A page of users.
     *
     * <p>Spring Data's own {@code Page} serializes with a shape that is awkward
     * to depend on and that it has warned about changing, so the four numbers
     * the table actually needs are spelled out here instead.
     */
    public record UserPage(
            List<AdminUserSummary> items,
            int page,
            int size,
            long totalItems,
            int totalPages) {
    }

    /** Everything the console shows about one account. */
    public record AdminUserDetail(
            AdminUserSummary user,
            SubscriptionResponse subscription,
            List<BaseResumeSummary> resumes,
            List<TailoredResumeSummary> tailorings) {
    }

    /**
     * An account created from the console rather than by signup.
     *
     * <p>The password is set by the administrator and handed over out of band.
     * There is no invite email because there is no mail transport in this
     * application yet, and a create-user button that silently does nothing
     * would be worse than one that is honest about requiring a password.
     */
    public record CreateUserRequest(
            @NotBlank(message = "Name is required") @Size(max = 120) String name,

            @NotBlank(message = "Email is required") @Email(message = "Enter a valid email")
            @Size(max = 255) String email,

            @NotBlank(message = "Password is required")
            @Size(min = 8, max = 72, message = "Use at least 8 characters") String password,

            /** Defaults to USER when absent, so the dangerous value is never the implicit one. */
            Role role,

            /** Defaults to FREE when absent. */
            Plan plan) {

        /** Normalized in the constructor so {@code @Email} validates the trimmed value. */
        public CreateUserRequest {
            email = Emails.normalize(email);
            if (role == null) {
                role = Role.USER;
            }
            if (plan == null) {
                plan = Plan.FREE;
            }
        }
    }

    public record SetActiveRequest(@NotNull(message = "active is required") Boolean active) {
    }

    public record SetRoleRequest(@NotNull(message = "Role is required") Role role) {
    }

    public record SetPlanRequest(@NotNull(message = "Plan is required") Plan plan) {
    }

    /**
     * Overrides one account's monthly allowance.
     *
     * <p>Capped well above anything a trial needs but short of a number that
     * would make the quota meaningless. The allowance is the only thing
     * standing between this installation and an unbounded bill from the model
     * provider, so "unlimited" is not on offer even to an administrator.
     */
    public record SetAllowanceRequest(
            @NotNull(message = "An allowance is required")
            @Min(value = 0, message = "An allowance cannot be negative")
            @Max(value = 10_000, message = "That is too large to be a monthly allowance")
            Integer monthlyTailorings) {
    }
}
