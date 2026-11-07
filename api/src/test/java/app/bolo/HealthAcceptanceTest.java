package app.bolo;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class HealthAcceptanceTest extends PostgresTestBase {

    @Autowired MockMvc mvc;
    @Autowired JdbcClient jdbc;

    @Test
    void healthIsUpUnderApiPrefix() throws Exception {
        mvc.perform(get("/api/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void flywayAppliedTheLearnerSchema() {
        Integer tables = jdbc.sql("""
                select count(*) from information_schema.tables
                where table_schema = 'bolo' and table_name in ('learner', 'consent_record', 'web_session')
                """).query(Integer.class).single();
        assertThat(tables).isEqualTo(3);
    }
}
