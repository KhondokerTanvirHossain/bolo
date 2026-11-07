package app.bolo.speech.adapter.out.azure;

import app.bolo.speech.application.port.in.PronunciationResult;
import app.bolo.speech.application.port.in.SpeechBusyException;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The REST scripted assessment against a local stand-in for Azure, replaying real responses. */
class AzureScriptedAssessmentTest {

    HttpServer server;
    final AtomicReference<String> header = new AtomicReference<>();

    static String fixture(String name) throws IOException {
        try (InputStream in = AzureScriptedAssessmentTest.class.getResourceAsStream("/audio/" + name)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    AzureScriptedAssessment serving(int status, String body) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", ex -> {
            header.set(ex.getRequestHeaders().getFirst("Pronunciation-Assessment"));
            ex.getRequestBody().readAllBytes();
            byte[] b = body.getBytes(StandardCharsets.UTF_8);
            ex.sendResponseHeaders(status, b.length);
            ex.getResponseBody().write(b);
            ex.close();
        });
        server.start();
        return new AzureScriptedAssessment("key", "http://127.0.0.1:" + server.getAddress().getPort());
    }

    @AfterEach
    void stop() {
        if (server != null) server.stop(0);
    }

    @Test
    void readsWordsAndMiscuesWithTheReferenceSent() throws Exception {
        PronunciationResult r = serving(200, fixture("pa-scripted-ischool.json"))
                .assess(new byte[44], "I study at a small school near the station.");
        assertThat(r.heard()).isTrue();
        assertThat(r.words()).filteredOn(w -> w.errorType().equals("Insertion")).hasSize(2);
        String sent = new String(Base64.getDecoder().decode(header.get()), StandardCharsets.UTF_8);
        assertThat(sent).contains("\"ReferenceText\":\"I study at a small school near the station.\"")
                .contains("\"EnableMiscue\":true").contains("\"PhonemeAlphabet\":\"IPA\"");
    }

    @Test
    void nothingHeardIsNotAnError() throws Exception {
        PronunciationResult r = serving(200, "{\"RecognitionStatus\":\"NoMatch\",\"Offset\":0,\"Duration\":0}").assess(new byte[44], "Hello.");
        assertThat(r.heard()).isFalse();
        assertThat(r.words()).isEmpty();
    }

    @Test
    void aBusyVendorSaysSo() throws Exception {
        AzureScriptedAssessment a = serving(429, "{}");
        assertThatThrownBy(() -> a.assess(new byte[44], "Hello.")).isInstanceOf(SpeechBusyException.class);
    }
}
