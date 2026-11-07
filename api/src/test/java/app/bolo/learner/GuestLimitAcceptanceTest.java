package app.bolo.learner;

import app.bolo.PostgresTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** A script can't fill the shared box's disk with guests: new guests are capped per client IP per hour. */
@SpringBootTest(properties = "bolo.guests-per-ip-per-hour=3")
@AutoConfigureMockMvc
class GuestLimitAcceptanceTest extends PostgresTestBase {

    @Autowired MockMvc mvc;

    void newGuest(String ip, int expectedStatus) throws Exception {
        mvc.perform(post("/api/session").header("Origin", "http://localhost:3000").header("X-Bolo", "1")
                        .header("X-Forwarded-For", ip).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().is(expectedStatus));
    }

    @Test
    void theFourthNewGuestFromOneIpWithinAnHourIsTold429() throws Exception {
        newGuest("203.0.113.7", 200);
        newGuest("203.0.113.7", 200);
        newGuest("203.0.113.7", 200);
        mvc.perform(post("/api/session").header("Origin", "http://localhost:3000").header("X-Bolo", "1")
                        .header("X-Forwarded-For", "203.0.113.7").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("slow-down"));
        newGuest("198.51.100.9", 200);
    }
}
