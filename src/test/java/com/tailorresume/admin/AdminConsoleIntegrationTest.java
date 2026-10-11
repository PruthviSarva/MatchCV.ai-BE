package com.tailorresume.admin;

import com.tailorresume.billing.SubscriptionService;
import com.tailorresume.user.Role;
import com.tailorresume.user.UserAccount;
import com.tailorresume.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The admin console, end to end.
 *
 * <p>The tests that matter most here are not the happy paths. They are the
 * three ways this feature could go wrong in a way nobody notices until it
 * does real damage: an ordinary user reaching the console, a disabled account
 * carrying on with the token it already held, and an administrator locking the
 * last administrator out of the application.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AdminConsoleIntegrationTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    UserRepository users;

    @Autowired
    SubscriptionService subscriptions;

    @Autowired
    PasswordEncoder passwordEncoder;

    @Autowired
    ObjectMapper json;

    private static final String PASSWORD = "testpass123";

    private String adminToken;
    private Long adminId;

    @BeforeEach
    void seedAdmin() throws Exception {
        UserAccount admin = createAccount("Ada Admin", "ada@example.com", Role.ADMIN);
        adminId = admin.getId();
        adminToken = login("ada@example.com");
    }

    /**
     * Creates an account the way signup does -- user row and subscription row
     * together. Going through the repository rather than the signup endpoint is
     * what lets a test make an ADMIN, which no public endpoint can do.
     */
    private UserAccount createAccount(String name, String email, Role role) {
        UserAccount user = UserAccount.createWithRole(
                name, email, passwordEncoder.encode(PASSWORD), role);
        users.save(user);
        subscriptions.startFreePlan(user);
        return user;
    }

    private String login(String email) throws Exception {
        String body = mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"%s"}
                                """.formatted(email, PASSWORD)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(body).get("accessToken").asString();
    }

    private JsonNode getJson(String path, String token) throws Exception {
        String body = mvc.perform(get(path).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(body);
    }

    /* ------------------------------------------------------------- access */

    @Test
    @DisplayName("an ordinary user cannot reach the admin console")
    void regularUserIsForbidden() throws Exception {
        createAccount("Raj User", "raj@example.com", Role.USER);
        String userToken = login("raj@example.com");

        mvc.perform(get("/api/admin/overview").header("Authorization", "Bearer " + userToken))
                .andExpect(status().isForbidden());

        mvc.perform(get("/api/admin/users").header("Authorization", "Bearer " + userToken))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("the admin console is not reachable without a token at all")
    void anonymousIsUnauthorized() throws Exception {
        mvc.perform(get("/api/admin/overview")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("an admin sees every account, with its plan and its counts")
    void adminSeesEveryAccount() throws Exception {
        createAccount("Raj User", "raj@example.com", Role.USER);

        JsonNode page = getJson("/api/admin/users", adminToken);

        assertThat(page.get("totalItems").asLong()).isEqualTo(2);
        assertThat(page.get("items")).hasSize(2);
        // Newest first, and the subscription that signup creates is joined on.
        assertThat(page.get("items").get(0).get("email").asString()).isEqualTo("raj@example.com");
        assertThat(page.get("items").get(0).get("plan").asString()).isEqualTo("FREE");
        assertThat(page.get("items").get(0).get("tailoringCount").asLong()).isZero();
    }

    @Test
    @DisplayName("the overview counts accounts by state")
    void overviewCountsAccounts() throws Exception {
        createAccount("Raj User", "raj@example.com", Role.USER);
        UserAccount disabled = createAccount("Mia User", "mia@example.com", Role.USER);
        disabled.deactivate();
        users.save(disabled);

        JsonNode overview = getJson("/api/admin/overview", adminToken);

        assertThat(overview.get("totalUsers").asLong()).isEqualTo(3);
        assertThat(overview.get("activeUsers").asLong()).isEqualTo(2);
        assertThat(overview.get("disabledUsers").asLong()).isEqualTo(1);
        assertThat(overview.get("admins").asLong()).isEqualTo(1);
        assertThat(overview.get("freePlans").asLong()).isEqualTo(3);
        // Nothing has been tailored, so there is no average to report.
        assertThat(overview.get("averageMatchScore").isNull()).isTrue();
    }

    /* ------------------------------------------------------------ creating */

    @Test
    @DisplayName("an account created from the console can log in with the password it was given")
    void createdUserCanLogIn() throws Exception {
        mvc.perform(post("/api/admin/users")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Nina New","email":"  NINA@Example.COM ",
                                 "password":"brandnewpass","plan":"PRO"}
                                """))
                .andExpect(status().isCreated())
                // The address is normalized by the same rule signup uses, or
                // the account could never be logged into.
                .andExpect(jsonPath("$.user.email").value("nina@example.com"))
                .andExpect(jsonPath("$.user.role").value("USER"))
                .andExpect(jsonPath("$.user.active").value(true))
                .andExpect(jsonPath("$.subscription.plan").value("PRO"));

        mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"nina@example.com","password":"brandnewpass"}
                                """))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("the console cannot create a second account on an address already taken")
    void createRejectsDuplicateEmail() throws Exception {
        createAccount("Raj User", "raj@example.com", Role.USER);

        mvc.perform(post("/api/admin/users")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Impostor","email":"raj@example.com",
                                 "password":"anotherpass1"}
                                """))
                .andExpect(status().isConflict());
    }

    /* ---------------------------------------------------------- disabling */

    @Test
    @DisplayName("disabling an account kills the token it is already holding")
    void disablingRevokesTheLiveSession() throws Exception {
        createAccount("Raj User", "raj@example.com", Role.USER);
        String userToken = login("raj@example.com");
        Long rajId = users.findByEmail("raj@example.com").orElseThrow().getId();

        // The session works right up to the moment it is disabled.
        mvc.perform(get("/api/me").header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk());

        mvc.perform(patch("/api/admin/users/" + rajId + "/active")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"active":false}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.active").value(false));

        // The access token is still cryptographically valid and still inside
        // its half hour. It stops working anyway, because the filter re-reads
        // the account rather than trusting the claims. This is the assertion
        // that makes "disable" mean anything.
        mvc.perform(get("/api/me").header("Authorization", "Bearer " + userToken))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("a disabled account cannot log back in to get a fresh token")
    void disabledAccountCannotLogIn() throws Exception {
        UserAccount raj = createAccount("Raj User", "raj@example.com", Role.USER);

        mvc.perform(patch("/api/admin/users/" + raj.getId() + "/active")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"active":false}
                                """))
                .andExpect(status().isOk());

        mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"raj@example.com","password":"%s"}
                                """.formatted(PASSWORD)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("re-enabling an account lets it log in again")
    void reEnablingRestoresAccess() throws Exception {
        UserAccount raj = createAccount("Raj User", "raj@example.com", Role.USER);
        String path = "/api/admin/users/" + raj.getId() + "/active";

        mvc.perform(patch(path).header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"active\":false}"))
                .andExpect(status().isOk());

        mvc.perform(patch(path).header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"active\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.active").value(true));

        assertThat(login("raj@example.com")).isNotBlank();
    }

    /* ------------------------------------------------------- self-lockout */

    @Test
    @DisplayName("an admin cannot disable their own account")
    void adminCannotDisableThemselves() throws Exception {
        mvc.perform(patch("/api/admin/users/" + adminId + "/active")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"active\":false}"))
                .andExpect(status().isBadRequest());

        assertThat(users.findById(adminId).orElseThrow().isActive()).isTrue();
    }

    @Test
    @DisplayName("an admin cannot drop their own administrator access")
    void adminCannotDemoteThemselves() throws Exception {
        mvc.perform(patch("/api/admin/users/" + adminId + "/role")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"USER\"}"))
                .andExpect(status().isBadRequest());

        assertThat(users.findById(adminId).orElseThrow().getRole()).isEqualTo(Role.ADMIN);
    }

    @Test
    @DisplayName("the last active admin cannot be demoted, even by another admin")
    void theLastAdminIsProtected() throws Exception {
        // A second admin, so the first one is reachable by somebody.
        UserAccount second = createAccount("Bo Admin", "bo@example.com", Role.ADMIN);
        String secondToken = login("bo@example.com");

        // Bo demotes Ada. Allowed: Bo is still an admin afterwards.
        mvc.perform(patch("/api/admin/users/" + adminId + "/role")
                        .header("Authorization", "Bearer " + secondToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"USER\"}"))
                .andExpect(status().isOk());

        // Ada is now an ordinary user, so Bo is the only admin left. Ada's own
        // token must also have stopped being an admin token at the same moment.
        mvc.perform(get("/api/admin/overview").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isForbidden());

        // Promote Ada back so there is somebody to attempt the lockout with.
        mvc.perform(patch("/api/admin/users/" + adminId + "/role")
                        .header("Authorization", "Bearer " + secondToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"ADMIN\"}"))
                .andExpect(status().isOk());

        // Now disable Bo as Ada, leaving Ada alone. Allowed.
        mvc.perform(patch("/api/admin/users/" + second.getId() + "/active")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"active\":false}"))
                .andExpect(status().isOk());

        // Ada is the only enabled admin. Nothing may take that away.
        mvc.perform(patch("/api/admin/users/" + adminId + "/role")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"USER\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("promoting a user takes effect on their next request")
    void promotionIsImmediate() throws Exception {
        UserAccount raj = createAccount("Raj User", "raj@example.com", Role.USER);
        String userToken = login("raj@example.com");

        mvc.perform(get("/api/admin/overview").header("Authorization", "Bearer " + userToken))
                .andExpect(status().isForbidden());

        mvc.perform(patch("/api/admin/users/" + raj.getId() + "/role")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"ADMIN\"}"))
                .andExpect(status().isOk());

        // Same token as before. The role is read from the row, not the claims.
        mvc.perform(get("/api/admin/overview").header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk());
    }

    /* -------------------------------------------------------- search, plan */

    @Test
    @DisplayName("the user search matches name or email, and filters on the disabled flag")
    void searchFiltersAccounts() throws Exception {
        createAccount("Raj Patel", "raj@example.com", Role.USER);
        UserAccount mia = createAccount("Mia Stone", "mia@elsewhere.test", Role.USER);
        mia.deactivate();
        users.save(mia);

        assertThat(getJson("/api/admin/users?query=patel", adminToken)
                .get("totalItems").asLong()).isEqualTo(1);
        assertThat(getJson("/api/admin/users?query=elsewhere", adminToken)
                .get("totalItems").asLong()).isEqualTo(1);
        assertThat(getJson("/api/admin/users?active=false", adminToken)
                .get("totalItems").asLong()).isEqualTo(1);
        assertThat(getJson("/api/admin/users?role=ADMIN", adminToken)
                .get("totalItems").asLong()).isEqualTo(1);
        // An underscore is a SQL wildcard; escaping it is what stops this
        // matching every address with any character between "mi" and "a".
        assertThat(getJson("/api/admin/users?query=mi_a", adminToken)
                .get("totalItems").asLong()).isZero();
    }

    @Test
    @DisplayName("an admin can move an account between plans")
    void adminCanSetThePlan() throws Exception {
        UserAccount raj = createAccount("Raj User", "raj@example.com", Role.USER);
        String path = "/api/admin/users/" + raj.getId() + "/plan";

        mvc.perform(patch(path).header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"plan\":\"PRO\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subscription.plan").value("PRO"));

        // Setting the plan it already has is a no-op, not a 409 and not a fresh
        // allowance -- see SubscriptionService#setPlan.
        mvc.perform(patch(path).header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"plan\":\"PRO\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subscription.plan").value("PRO"));

        mvc.perform(patch(path).header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"plan\":\"FREE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subscription.plan").value("FREE"));
    }

    /* ------------------------------------------------------- the allowance */

    @Test
    @DisplayName("an admin can raise one account's allowance without touching anyone else")
    void allowanceIsPerAccount() throws Exception {
        UserAccount raj = createAccount("Raj User", "raj@example.com", Role.USER);
        UserAccount mia = createAccount("Mia User", "mia@example.com", Role.USER);

        // Both start on the configured free allowance (2 in the test profile).
        mvc.perform(patch("/api/admin/users/" + raj.getId() + "/allowance")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"monthlyTailorings\":30}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subscription.monthlyTailorings").value(30))
                .andExpect(jsonPath("$.subscription.remaining").value(30));

        // Mia is untouched. This is the whole point of storing the number on
        // the row rather than reading it from configuration.
        assertThat(getJson("/api/admin/users/" + mia.getId(), adminToken)
                .get("subscription").get("monthlyTailorings").asInt()).isEqualTo(2);

        // And the raised allowance is what that account itself is told.
        String rajToken = login("raj@example.com");
        assertThat(getJson("/api/me", rajToken)
                .get("subscription").get("monthlyTailorings").asInt()).isEqualTo(30);
    }

    @Test
    @DisplayName("changing the plan resets the allowance to that plan's default")
    void planChangeResetsTheAllowance() throws Exception {
        UserAccount raj = createAccount("Raj User", "raj@example.com", Role.USER);

        mvc.perform(patch("/api/admin/users/" + raj.getId() + "/allowance")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"monthlyTailorings\":30}"))
                .andExpect(status().isOk());

        // PRO is 5 in the test profile. An override belongs to an account on a
        // plan; moving the plan discards it rather than carrying 30 across.
        mvc.perform(patch("/api/admin/users/" + raj.getId() + "/plan")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"plan\":\"PRO\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subscription.monthlyTailorings").value(5));
    }

    @Test
    @DisplayName("a negative allowance is refused")
    void negativeAllowanceIsRejected() throws Exception {
        UserAccount raj = createAccount("Raj User", "raj@example.com", Role.USER);

        mvc.perform(patch("/api/admin/users/" + raj.getId() + "/allowance")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"monthlyTailorings\":-5}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("an unknown account id is a 404, not an empty page")
    void unknownUserIsNotFound() throws Exception {
        mvc.perform(get("/api/admin/users/999999").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNotFound());
    }
}
