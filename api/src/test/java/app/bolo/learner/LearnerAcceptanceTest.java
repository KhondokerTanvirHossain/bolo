package app.bolo.learner;

import app.bolo.PostgresTestBase;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class LearnerAcceptanceTest extends PostgresTestBase {

    static final String ORIGIN = "http://localhost:3000";

    @Autowired MockMvc mvc;
    @Autowired JdbcClient jdbc;

    static MockHttpServletRequestBuilder write(MockHttpServletRequestBuilder req) {
        return req.header("Origin", ORIGIN).header("X-Bolo", "1").contentType(MediaType.APPLICATION_JSON);
    }

    Cookie startSession() throws Exception {
        MvcResult res = mvc.perform(write(post("/api/session")).content("{}"))
                .andExpect(status().isOk())
                .andExpect(cookie().httpOnly("bolo_session", true))
                .andExpect(jsonPath("$.kind").value("guest"))
                .andReturn();
        return res.getResponse().getCookie("bolo_session");
    }

    @Test
    void sessionIsIdempotentWithAValidCookie() throws Exception {
        Cookie cookie = startSession();
        String first = mvc.perform(get("/api/me").cookie(cookie)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String second = mvc.perform(write(post("/api/session")).cookie(cookie).content("{}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(second).isEqualTo(first);
        assertThat(jdbc.sql("select count(*) from learner").query(Integer.class).single()).isGreaterThanOrEqualTo(1);
    }

    @Test
    void meWithoutACookieIsSignedOut() throws Exception {
        mvc.perform(get("/api/me")).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("signed-out"));
    }

    @Test
    void consentTwiceAppendsTwoRowsAndTheLatestWins() throws Exception {
        Cookie cookie = startSession();
        mvc.perform(write(post("/api/consent")).cookie(cookie)
                        .content("{\"keepRecordings\":true,\"over13\":true,\"policyVersion\":\"2026-11-v1\"}"))
                .andExpect(status().isNoContent());
        mvc.perform(write(post("/api/consent")).cookie(cookie)
                        .content("{\"keepRecordings\":false,\"over13\":true,\"policyVersion\":\"2026-11-v1\"}"))
                .andExpect(status().isNoContent());

        String learnerId = com.jayway.jsonpath.JsonPath.read(
                mvc.perform(get("/api/me").cookie(cookie))
                        .andExpect(jsonPath("$.consent.keepRecordings").value(false))
                        .andExpect(jsonPath("$.consent.policyVersion").value("2026-11-v1"))
                        .andReturn().getResponse().getContentAsString(), "$.learnerId");
        Integer rows = jdbc.sql("select count(*) from consent_record where learner_id = ?")
                .param(UUID.fromString(learnerId)).query(Integer.class).single();
        assertThat(rows).isEqualTo(2);
    }

    @Test
    void underThirteenIsRefused() throws Exception {
        Cookie cookie = startSession();
        mvc.perform(write(post("/api/consent")).cookie(cookie)
                        .content("{\"keepRecordings\":true,\"over13\":false,\"policyVersion\":\"2026-11-v1\"}"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("under-13"));
    }

    @Test
    void consentWithoutASessionIsSignedOut() throws Exception {
        mvc.perform(write(post("/api/consent"))
                        .content("{\"keepRecordings\":true,\"over13\":true,\"policyVersion\":\"2026-11-v1\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void writeWithoutTheBoloHeaderIsForbidden() throws Exception {
        mvc.perform(post("/api/session").header("Origin", ORIGIN).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("forbidden"));
    }

    @Test
    void writeFromAnotherOriginIsForbidden() throws Exception {
        mvc.perform(post("/api/session").header("Origin", "https://evil.example").header("X-Bolo", "1")
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void writeAsFormPostIsForbidden() throws Exception {
        mvc.perform(post("/api/session").header("Origin", ORIGIN).header("X-Bolo", "1")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED).content("a=b"))
                .andExpect(status().isForbidden());
    }
}
