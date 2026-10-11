package com.tailorresume.admin;

import com.tailorresume.admin.AdminDtos.ActivityEntry;
import com.tailorresume.admin.AdminDtos.AdminUserDetail;
import com.tailorresume.admin.AdminDtos.CreateUserRequest;
import com.tailorresume.admin.AdminDtos.OverviewResponse;
import com.tailorresume.admin.AdminDtos.SetActiveRequest;
import com.tailorresume.admin.AdminDtos.SetAllowanceRequest;
import com.tailorresume.admin.AdminDtos.SetPlanRequest;
import com.tailorresume.admin.AdminDtos.SetRoleRequest;
import com.tailorresume.admin.AdminDtos.UserPage;
import com.tailorresume.security.AuthenticatedUser;
import com.tailorresume.user.Role;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * The admin console's API.
 *
 * <p>There is no authorization annotation on any of these methods, and that is
 * deliberate: {@code SecurityConfig} gates the entire {@code /api/admin/**}
 * prefix on ROLE_ADMIN. A rule on the prefix covers an endpoint added here
 * tomorrow; a rule per method covers only the methods somebody remembered to
 * annotate.
 *
 * <p>Note what is absent. There is no delete-account endpoint. Disabling is
 * reversible, keeps the audit trail that the activity feed is built from, and
 * answers every reason an administrator actually reaches for delete; a cascade
 * that silently destroys someone's resumes and their whole tailoring history
 * is not something to put one click away.
 */
@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final AdminService admin;

    public AdminController(AdminService admin) {
        this.admin = admin;
    }

    @GetMapping("/overview")
    public OverviewResponse overview() {
        return admin.overview();
    }

    @GetMapping("/activity")
    public List<ActivityEntry> activity() {
        return admin.activity();
    }

    /**
     * The user table.
     *
     * @param query  free text matched against name and email
     * @param active {@code null} for every account, otherwise only enabled or
     *               only disabled ones
     * @param role   {@code null} for every role
     */
    @GetMapping("/users")
    public UserPage users(@RequestParam(required = false) String query,
                          @RequestParam(required = false) Boolean active,
                          @RequestParam(required = false) Role role,
                          @RequestParam(defaultValue = "0") int page,
                          @RequestParam(defaultValue = "20") int size) {
        return admin.search(query, active, role, page, size);
    }

    @GetMapping("/users/{id}")
    public AdminUserDetail user(@PathVariable Long id) {
        return admin.detail(id);
    }

    @PostMapping("/users")
    @ResponseStatus(HttpStatus.CREATED)
    public AdminUserDetail create(@Valid @RequestBody CreateUserRequest request) {
        return admin.create(request);
    }

    /**
     * Enable or disable an account.
     *
     * <p>The acting administrator is taken from the authenticated principal,
     * never from the request body -- the self-lockout guard is worth nothing if
     * the caller gets to say who they are.
     */
    @PatchMapping("/users/{id}/active")
    public AdminUserDetail setActive(@AuthenticationPrincipal AuthenticatedUser principal,
                                     @PathVariable Long id,
                                     @Valid @RequestBody SetActiveRequest request) {
        return admin.setActive(principal.id(), id, request.active());
    }

    @PatchMapping("/users/{id}/role")
    public AdminUserDetail setRole(@AuthenticationPrincipal AuthenticatedUser principal,
                                   @PathVariable Long id,
                                   @Valid @RequestBody SetRoleRequest request) {
        return admin.setRole(principal.id(), id, request.role());
    }

    @PatchMapping("/users/{id}/plan")
    public AdminUserDetail setPlan(@PathVariable Long id,
                                   @Valid @RequestBody SetPlanRequest request) {
        return admin.setPlan(id, request.plan());
    }

    /**
     * Overrides this account's monthly allowance.
     *
     * <p>Separate from the plan endpoint on purpose. Changing plan resets the
     * allowance to that plan's default, so folding the two together would make
     * "give this tester 30 rewrites" and "put them on Pro" the same request --
     * and one of them would silently undo the other.
     */
    @PatchMapping("/users/{id}/allowance")
    public AdminUserDetail setAllowance(@PathVariable Long id,
                                        @Valid @RequestBody SetAllowanceRequest request) {
        return admin.setAllowance(id, request.monthlyTailorings());
    }
}
