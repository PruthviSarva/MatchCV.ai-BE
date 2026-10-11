package com.tailorresume.billing;

import com.tailorresume.support.FixedResumeTailorClient;
import com.tailorresume.tailor.ResumeTailorClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The whole tailoring pipeline against a canned model client: quota, the model
 * call, persistence and the download.
 *
 * <p>The free allowance is 2 in {@code application-test.yml}, which is what
 * makes exhausting it cheap enough to assert on.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class QuotaEnforcementIntegrationTest {

    /**
     * Replaces the Claude client for this test. A {@code @Primary} bean rather
     * than a mock, so the test exercises the real service and controller paths
     * and only the network call is substituted.
     */
    @TestConfiguration
    static class FakeClientConfig {

        @Bean
        @Primary
        FixedResumeTailorClient fixedResumeTailorClient() {
            return new FixedResumeTailorClient();
        }
    }

    private static final String JOB_DESCRIPTION = """
            Senior Backend Engineer
            We need someone who has shipped payment systems on AWS, is fluent in
            Java and Spring Boot, and has run Kafka consumers in production.
            Kubernetes experience is a plus.""";

    private static final String RESUME_TEXT = """
            Priya Raman
            priya.raman@example.com
            Senior Backend Engineer at Northwind Payments, 2021 to present.
            Built settlement ledger services in Java and Spring Boot on AWS.
            Ran Kafka consumers handling 40 million dollars of settlement a month.
            B.S. Computer Science, University of Texas at Austin.""";

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper json;

    @Autowired
    FixedResumeTailorClient tailorClient;

    @Autowired
    ResumeTailorClient injectedClient;

    private String signUp(String email) throws Exception {
        String body = mvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Priya","email":"%s","password":"testpass123"}"""
                                .formatted(email)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(body).path("accessToken").asString();
    }

    private static String tailorBody() {
        return """
                {"resumeText":%s,"jobDescription":%s}"""
                .formatted(quote(RESUME_TEXT), quote(JOB_DESCRIPTION));
    }

    private static String quote(String value) {
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n") + "\"";
    }

    @Test
    @DisplayName("the fake client really is the one wired in")
    void fakeClientIsWired() {
        assertThat(injectedClient).isSameAs(tailorClient);
    }

    @Test
    @DisplayName("a tailoring returns the structured resume and counts against the allowance")
    void tailoringSucceedsAndCounts() throws Exception {
        String token = signUp("tailor@example.com");

        mvc.perform(post("/api/tailorings")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(tailorBody()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.resume.name").value("Priya Raman"))
                .andExpect(jsonPath("$.resume.skills[0]").value("Java"))
                .andExpect(jsonPath("$.matchScore").value(78))
                // Derived from the posting, not asked for a second time.
                .andExpect(jsonPath("$.jobTitle").value("Senior Backend Engineer"))
                // The gap report is the honest half of the output.
                .andExpect(jsonPath("$.resume.missingKeywords[0]").value("Kubernetes"));

        mvc.perform(get("/api/billing/subscription").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.used").value(1))
                .andExpect(jsonPath("$.remaining").value(1));
    }

    @Test
    @DisplayName("exhausting the free allowance returns 402 and does not call the model")
    void exhaustedQuotaIsRejectedBeforeTheModelRuns() throws Exception {
        String token = signUp("quota@example.com");

        for (int i = 0; i < 2; i++) {
            mvc.perform(post("/api/tailorings")
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(tailorBody()))
                    .andExpect(status().isCreated());
        }

        int callsBefore = tailorClient.callCount();

        mvc.perform(post("/api/tailorings")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(tailorBody()))
                // 402 rather than 403, so the frontend can tell "upgrade" apart
                // from "not yours".
                .andExpect(status().isPaymentRequired())
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.containsString("Upgrade to Pro")));

        // The point of checking quota first: a refused request costs nothing.
        assertThat(tailorClient.callCount()).isEqualTo(callsBefore);
    }

    @Test
    @DisplayName("upgrading to Pro raises the allowance and resets the period")
    void upgradingRaisesTheAllowance() throws Exception {
        String token = signUp("upgrade@example.com");

        mvc.perform(post("/api/tailorings")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(tailorBody()))
                .andExpect(status().isCreated());

        mvc.perform(post("/api/billing/subscribe")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"plan":"PRO"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.plan").value("PRO"))
                .andExpect(jsonPath("$.monthlyTailorings").value(5))
                // A fresh period: the free tailoring already spent must not
                // count against the plan the user just paid for.
                .andExpect(jsonPath("$.used").value(0))
                // Flagged honestly until a gateway is wired in.
                .andExpect(jsonPath("$.checkoutStubbed").value(true));
    }

    @Test
    @DisplayName("cancelling keeps Pro until the period ends")
    void cancelKeepsAccessUntilPeriodEnd() throws Exception {
        String token = signUp("cancel@example.com");

        mvc.perform(post("/api/billing/subscribe")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"plan":"PRO"}"""))
                .andExpect(status().isOk());

        mvc.perform(post("/api/billing/cancel").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELED"))
                // Still PRO, still the PRO allowance: cancel means "do not renew".
                .andExpect(jsonPath("$.plan").value("PRO"))
                .andExpect(jsonPath("$.monthlyTailorings").value(5));
    }

    @Test
    @DisplayName("a tailoring is downloadable as both DOCX and PDF")
    void downloadsBothFormats() throws Exception {
        String token = signUp("download@example.com");

        String created = mvc.perform(post("/api/tailorings")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(tailorBody()))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long id = json.readTree(created).path("id").asLong();

        byte[] docx = mvc.perform(get("/api/tailorings/" + id + "/download?format=docx")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();

        byte[] pdf = mvc.perform(get("/api/tailorings/" + id + "/download?format=pdf")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();

        // Magic bytes: a DOCX is a zip, a PDF starts with %PDF.
        assertThat(new String(docx, 0, 2)).isEqualTo("PK");
        assertThat(new String(pdf, 0, 4)).isEqualTo("%PDF");

        mvc.perform(get("/api/tailorings/" + id + "/download?format=rtf")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("one user cannot read or download another user's tailoring")
    void tailoringsAreOwnerScoped() throws Exception {
        String owner = signUp("owner@example.com");
        String other = signUp("other@example.com");

        String created = mvc.perform(post("/api/tailorings")
                        .header("Authorization", "Bearer " + owner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(tailorBody()))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long id = json.readTree(created).path("id").asLong();

        // 404 rather than 403: the other account must not learn the row exists.
        mvc.perform(get("/api/tailorings/" + id).header("Authorization", "Bearer " + other))
                .andExpect(status().isNotFound());

        mvc.perform(get("/api/tailorings/" + id + "/download?format=pdf")
                        .header("Authorization", "Bearer " + other))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("a job description too short to tailor against is rejected")
    void validatesJobDescription() throws Exception {
        String token = signUp("validate@example.com");

        mvc.perform(post("/api/tailorings")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"resumeText":%s,"jobDescription":"Backend dev"}"""
                                .formatted(quote(RESUME_TEXT))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.jobDescription").isNotEmpty());
    }
}
