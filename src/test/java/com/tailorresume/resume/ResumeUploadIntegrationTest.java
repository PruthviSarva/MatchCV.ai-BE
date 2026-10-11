package com.tailorresume.resume;

import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Upload through the HTTP boundary — multipart in, extracted text out.
 *
 * <p>The frontend sends the file and nothing else; all parsing happens here, so
 * this is the test that proves the browser no longer needs a PDF library.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ResumeUploadIntegrationTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper json;

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

    private static byte[] docx() throws IOException {
        try (XWPFDocument document = new XWPFDocument();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            for (String line : new String[]{
                    "Priya Raman",
                    "priya.raman@example.com | Austin, TX",
                    "EXPERIENCE",
                    "Senior Backend Engineer, Northwind Payments, 2021 - Present",
                    "Rebuilt the settlement ledger as idempotent Kafka consumers.",
                    "EDUCATION",
                    "B.S. Computer Science, University of Texas at Austin"}) {
                document.createParagraph().createRun().setText(line);
            }
            document.write(out);
            return out.toByteArray();
        }
    }

    @Test
    @DisplayName("a DOCX upload is parsed server-side and saved against the account")
    void uploadsAndExtracts() throws Exception {
        String token = signUp("upload@example.com");

        MockMultipartFile file = new MockMultipartFile(
                "file", "Priya_Resume.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                docx());

        mvc.perform(multipart("/api/resumes").file(file)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sourceType").value("docx"))
                // Labelled from the filename, so the user recognises it later.
                .andExpect(jsonPath("$.label").value("Priya_Resume"))
                .andExpect(jsonPath("$.extractedText")
                        .value(org.hamcrest.Matchers.containsString("Kafka consumers")));

        mvc.perform(get("/api/resumes").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                // The list view must not ship resume text.
                .andExpect(jsonPath("$[0].extractedText").doesNotExist());
    }

    @Test
    @DisplayName("a saved resume can be tailored by id without re-uploading")
    void savedResumeIsReusable() throws Exception {
        String token = signUp("reuse@example.com");

        MockMultipartFile file = new MockMultipartFile(
                "file", "resume.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                docx());

        String created = mvc.perform(multipart("/api/resumes").file(file)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long id = json.readTree(created).path("id").asLong();

        mvc.perform(get("/api/resumes/" + id).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.extractedText")
                        .value(org.hamcrest.Matchers.containsString("Priya Raman")));
    }

    @Test
    @DisplayName("one user cannot read another user's uploaded resume")
    void resumesAreOwnerScoped() throws Exception {
        String owner = signUp("resume-owner@example.com");
        String other = signUp("resume-other@example.com");

        MockMultipartFile file = new MockMultipartFile(
                "file", "resume.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                docx());

        String created = mvc.perform(multipart("/api/resumes").file(file)
                        .header("Authorization", "Bearer " + owner))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long id = json.readTree(created).path("id").asLong();

        mvc.perform(get("/api/resumes/" + id).header("Authorization", "Bearer " + other))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("uploading requires a token")
    void uploadRequiresAuth() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "resume.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                docx());

        mvc.perform(multipart("/api/resumes").file(file))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("tailoring refuses when no API key is configured, rather than faking a resume")
    void stubClientRefusesLoudly() throws Exception {
        // This test does NOT substitute the model client, so the stub is live —
        // which is the production behaviour when CLAUDE_API_KEY is unset.
        String token = signUp("stub@example.com");

        mvc.perform(post("/api/tailorings")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"resumeText":"Priya Raman is a senior backend engineer who \
                                built settlement ledger services in Java and Spring Boot on AWS \
                                and ran Kafka consumers in production for four years.",\
                                "jobDescription":"Senior Backend Engineer. We need someone who \
                                has shipped payment systems on AWS and is fluent in Java and \
                                Spring Boot with Kafka in production."}"""))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.message")
                        .value(org.hamcrest.Matchers.containsString("not configured")));
    }
}
