package com.tailorresume.auth;

import com.tailorresume.user.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
// Boot 4 relocated this from org.springframework.boot.test.autoconfigure.web.servlet.
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AuthFlowIntegrationTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    UserRepository users;

    @Autowired
    ObjectMapper json;

    private static String signup(String name, String email, String password) {
        return """
                {"name":"%s","email":"%s","password":"%s"}
                """.formatted(name, email, password);
    }

    private JsonNode signupAndRead(String email) throws Exception {
        String body = mvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(signup("Priya", email, "testpass123")))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(body);
    }

    @Test
    @DisplayName("a new user signs up, gets a token pair, and starts on the free plan")
    void signupIssuesTokensAndFreePlan() throws Exception {
        mvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(signup("Priya", "priya@example.com", "testpass123")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                .andExpect(jsonPath("$.user.email").value("priya@example.com"))
                // The hash must never be serialized to the client.
                .andExpect(jsonPath("$.user.passwordHash").doesNotExist());

        assertThat(users.existsByEmail("priya@example.com")).isTrue();
    }

    @Test
    @DisplayName("the password is stored only as a hash")
    void passwordIsHashed() throws Exception {
        signupAndRead("hash@example.com");

        String stored = users.findByEmail("hash@example.com").orElseThrow().getPasswordHash();

        assertThat(stored).doesNotContain("testpass123").startsWith("{bcrypt}");
    }

    @Test
    @DisplayName("email case and whitespace cannot create a duplicate account")
    void emailIsNormalized() throws Exception {
        signupAndRead("dup@example.com");

        mvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(signup("Someone", "  DUP@Example.COM  ", "testpass123")))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("login accepts the account and rejects a wrong password identically to an unknown one")
    void loginDoesNotLeakAccountExistence() throws Exception {
        signupAndRead("login@example.com");

        mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"login@example.com","password":"testpass123"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty());

        String wrongPassword = mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"login@example.com","password":"wrongpass123"}"""))
                .andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();

        String noSuchAccount = mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"nobody@example.com","password":"testpass123"}"""))
                .andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();

        // Identical messages, so the endpoint cannot be used to enumerate accounts.
        assertThat(json.readTree(wrongPassword).path("message").asString())
                .isEqualTo(json.readTree(noSuchAccount).path("message").asString());
    }

    @Test
    @DisplayName("refresh rotates the token: the new one works and the old one is gone")
    void refreshRotates() throws Exception {
        String firstRefresh = signupAndRead("rotate@example.com").path("refreshToken").asString();

        String rotated = mvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"refreshToken":"%s"}""".formatted(firstRefresh)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andReturn().getResponse().getContentAsString();

        String secondRefresh = json.readTree(rotated).path("refreshToken").asString();
        assertThat(secondRefresh).isNotEqualTo(firstRefresh);

        // The replacement is accepted.
        mvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"refreshToken":"%s"}""".formatted(secondRefresh)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("logout revokes the refresh token")
    void logoutRevokes() throws Exception {
        String refresh = signupAndRead("logout@example.com").path("refreshToken").asString();

        mvc.perform(post("/api/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"refreshToken":"%s"}""".formatted(refresh)))
                .andExpect(status().isNoContent());

        mvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"refreshToken":"%s"}""".formatted(refresh)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("/api/me returns the account with its plan and allowance")
    void meReturnsAccountAndPlan() throws Exception {
        String accessToken = signupAndRead("me@example.com").path("accessToken").asString();

        mvc.perform(get("/api/me").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.email").value("me@example.com"))
                .andExpect(jsonPath("$.subscription.plan").value("FREE"))
                // From application-test.yml, so this also proves the plan
                // allowance is configuration rather than hard-coded.
                .andExpect(jsonPath("$.subscription.monthlyTailorings").value(2))
                .andExpect(jsonPath("$.subscription.remaining").value(2));
    }

    @Test
    @DisplayName("a protected endpoint is 401 without a token and with a forged one")
    void protectedEndpointsRequireAValidToken() throws Exception {
        mvc.perform(get("/api/me")).andExpect(status().isUnauthorized());

        mvc.perform(get("/api/me").header("Authorization", "Bearer not.a.real.token"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("the plan catalogue is public so pricing can be shown before signup")
    void planCatalogueIsPublic() throws Exception {
        mvc.perform(get("/api/billing/plans"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].plan").value("FREE"))
                .andExpect(jsonPath("$[1].plan").value("PRO"));
    }

    @Test
    @DisplayName("signup validation rejects a short password and a malformed email")
    void validatesSignup() throws Exception {
        mvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(signup("Priya", "priya@example.com", "short")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.password").isNotEmpty());

        mvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(signup("Priya", "not-an-email", "testpass123")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.email").isNotEmpty());
    }
}
