package com.example.recruitmentsystem;

import com.example.recruitmentsystem.service.UserDirectory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * One test per finding in docs/security-review.md.
 *
 * <p>Each asserts the behaviour the original code got wrong, so a regression re-opens a specific,
 * documented vulnerability rather than merely failing a test.
 */
@SpringBootTest
@AutoConfigureMockMvc
class SecurityRegressionTest {

    private static final String PASSWORD = "Str0ng#Passw0rd!";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserDirectory directory;

    @BeforeEach
    void registerAccounts() throws Exception {
        register("alice", PASSWORD, "jobseeker");
        register("bob", PASSWORD, "employer");
    }

    private void register(String username, String password, String type) throws Exception {
        if (directory.exists(username)) {
            return;
        }
        mockMvc.perform(post("/api/auth/register").with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"username":"%s","password":"%s","userType":"%s"}""".formatted(username, password, type)));
    }

    @Nested
    @DisplayName("F-01 authentication bypass through the X-Username header")
    class AuthenticationBypass {

        @Test
        @DisplayName("a request carrying X-Username is not authenticated")
        void headerNoLongerAuthenticates() throws Exception {
            mockMvc.perform(get("/api/user/profile").header("X-Username", "alice"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("X-Username: admin does not grant administrator access")
        void headerDoesNotGrantAdmin() throws Exception {
            mockMvc.perform(delete("/api/admin/users/alice").with(csrf())
                            .header("X-Username", "admin"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("an authenticated caller cannot act as someone else by sending the header")
        void headerCannotOverrideThePrincipal() throws Exception {
            MvcResult result = mockMvc.perform(get("/api/user/profile")
                            .with(user("alice").roles("USER", "JOBSEEKER"))
                            .header("X-Username", "bob"))
                    .andExpect(status().isOk())
                    .andReturn();
            assertThat(result.getResponse().getContentAsString()).contains("alice");
        }
    }

    @Nested
    @DisplayName("F-02 broken access control on administrative endpoints")
    class AdministrativeAccess {

        @Test
        @DisplayName("an ordinary user cannot delete an account")
        void userCannotDelete() throws Exception {
            mockMvc.perform(delete("/api/admin/users/bob").with(csrf())
                            .with(user("alice").roles("USER")))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("an ordinary user cannot ban an account")
        void userCannotBan() throws Exception {
            mockMvc.perform(post("/api/admin/users/bob/ban").with(csrf())
                            .with(user("alice").roles("USER")))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("an unauthenticated caller cannot reach administrative endpoints")
        void anonymousCannotReachAdmin() throws Exception {
            mockMvc.perform(delete("/api/admin/users/bob").with(csrf()))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Nested
    @DisplayName("F-03 debug endpoints exposing accounts and password hashes")
    class DebugEndpoints {

        @Test
        @DisplayName("the user dump endpoint no longer exists")
        void userDumpIsGone() throws Exception {
            mockMvc.perform(get("/api/debug/users").with(user("admin").roles("ADMIN")))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("the lockout status endpoint no longer exists")
        void lockoutStatusIsGone() throws Exception {
            mockMvc.perform(get("/api/debug/lockout-status").with(user("admin").roles("ADMIN")))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("no response body contains a BCrypt hash")
        void noHashIsEverReturned() throws Exception {
            String profile = mockMvc.perform(get("/api/user/profile")
                            .with(user("alice").roles("USER", "JOBSEEKER")))
                    .andReturn().getResponse().getContentAsString();
            assertThat(profile).doesNotContain("$2a$").doesNotContain("$2b$");
        }
    }

    @Nested
    @DisplayName("F-04 hard-coded administrator credentials")
    class SeededAdministrator {

        @Test
        @DisplayName("the compiled-in admin/admin123 account does not exist")
        void defaultAdminIsGone() {
            assertThat(directory.find("admin")).isEmpty();
        }

        @Test
        @DisplayName("admin123 is rejected by the password policy")
        void weakPasswordIsRejected() {
            assertThat(directory.checkPasswordPolicy("admin123")).isNotNull();
        }
    }

    @Nested
    @DisplayName("F-05 account enumeration through login responses")
    class AccountEnumeration {

        @Test
        @DisplayName("a wrong password and an unknown user produce the same response")
        void responsesAreIndistinguishable() throws Exception {
            String wrongPassword = login("alice", "WrongPassword#1");
            String unknownUser = login("nobody-at-all", "WrongPassword#1");
            assertThat(wrongPassword).isEqualTo(unknownUser);
        }

        @Test
        @DisplayName("the response does not report the failed attempt count")
        void attemptCountIsNotDisclosed() throws Exception {
            assertThat(login("alice", "WrongPassword#1")).doesNotContain("Attempt");
        }

        private String login(String username, String password) throws Exception {
            return mockMvc.perform(post("/api/auth/login").with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"username":"%s","password":"%s"}""".formatted(username, password)))
                    .andExpect(status().isUnauthorized())
                    .andReturn().getResponse().getContentAsString();
        }
    }

    @Nested
    @DisplayName("F-08 missing CSRF protection")
    class CsrfProtection {

        @Test
        @DisplayName("a state-changing request without a CSRF token is rejected")
        void stateChangeRequiresToken() throws Exception {
            mockMvc.perform(post("/api/user/skills")
                            .with(user("alice").roles("USER", "JOBSEEKER"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"skills\":[\"java\"]}"))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("the same request with a token succeeds")
        void stateChangeSucceedsWithToken() throws Exception {
            mockMvc.perform(post("/api/user/skills").with(csrf())
                            .with(user("alice").roles("USER", "JOBSEEKER"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"skills\":[\"java\"]}"))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("a read does not require a token")
        void readsDoNotRequireToken() throws Exception {
            mockMvc.perform(get("/api/jobs")).andExpect(status().isOk());
        }
    }

    @Nested
    @DisplayName("F-10 password change accepted a username from the request body")
    class PasswordChange {

        @Test
        @DisplayName("the account changed is the authenticated one, not one named in the body")
        void changesOnlyTheCallersOwnPassword() throws Exception {
            mockMvc.perform(post("/api/auth/change-password").with(csrf())
                            .with(user("alice").roles("USER"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"username":"bob","currentPassword":"%s","newPassword":"An0ther#Passw0rd!"}"""
                                    .formatted(PASSWORD)))
                    .andExpect(status().isOk());

            // bob's password must be untouched.
            mockMvc.perform(post("/api/auth/login").with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"username":"bob","password":"%s"}""".formatted(PASSWORD)))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("a change requires authentication")
        void changeRequiresAuthentication() throws Exception {
            mockMvc.perform(post("/api/auth/change-password").with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"currentPassword":"x","newPassword":"An0ther#Passw0rd!"}"""))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Nested
    @DisplayName("Response hardening")
    class ResponseHardening {

        @Test
        @DisplayName("security headers are present")
        void securityHeaders() throws Exception {
            mockMvc.perform(get("/api/jobs"))
                    .andExpect(header().string("X-Frame-Options", "DENY"))
                    .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                    .andExpect(header().exists("Content-Security-Policy"));
        }

        @Test
        @DisplayName("unauthenticated API calls answer 401 JSON rather than a login redirect")
        void unauthenticatedIsJson() throws Exception {
            mockMvc.perform(get("/api/user/profile"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.error").exists());
        }
    }
}
