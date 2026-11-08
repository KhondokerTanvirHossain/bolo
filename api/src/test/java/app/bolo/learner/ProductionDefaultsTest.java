package app.bolo.learner;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/** Review: secure by default — without BOLO_AUTH_VENDOR the API verifies real Google tokens; only tests use the fake. */
class ProductionDefaultsTest {

    @Test
    void theIdentityVendorDefaultsToFirebase() throws Exception {
        String yml = Files.readString(Path.of("src/main/resources/application.yml"));
        assertThat(yml).contains("vendor: ${BOLO_AUTH_VENDOR:firebase}");
    }
}
