package app.bolo.assessment;

import app.bolo.PostgresTestBase;
import app.bolo.WavFixture;
import app.bolo.common.audio.Wav;
import app.bolo.speech.adapter.out.fake.FakeScriptedAssessment;
import app.bolo.speech.application.port.in.PronunciationResult;
import app.bolo.speech.application.port.in.ScoredWord;
import app.bolo.speech.application.port.in.SpeechBusyException;
import app.bolo.talk.TalkTestClient;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** POST /api/attempts and GET /api/practice with a fake scripted assessment. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "bolo.speech.vendor=fake", "bolo.llm.vendor=fake", "bolo.assessment.attempts-per-minute=5"})
class AttemptAcceptanceTest extends PostgresTestBase {

    @LocalServerPort int port;
    @Autowired FakeScriptedAssessment vendor;
    @Autowired JdbcClient jdbc;

    @org.junit.jupiter.api.BeforeEach
    void noLiveTalks() {
        jdbc.sql("update talk_session set ended_at = now(), end_reason = 'learner_ended' where ended_at is null").update();
    }

    @AfterEach
    void reset() {
        vendor.next(null);
        vendor.failWith(null);
    }

    static byte[] wav() {
        return Wav.wrap(WavFixture.pcm("/audio/practice-very-16k.wav"), 16000);
    }

    HttpResponse<String> attempt(String cookie, byte[] audio, String reference) throws Exception {
        return attempt(cookie, audio, reference, null);
    }

    HttpResponse<String> attempt(String cookie, byte[] audio, String reference, String mode) throws Exception {
        String boundary = "bolo" + System.nanoTime();
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        body.writeBytes(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"reference\"\r\n\r\n" + reference + "\r\n")
                .getBytes(StandardCharsets.UTF_8));
        if (mode != null) {
            body.writeBytes(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"mode\"\r\n\r\n" + mode + "\r\n")
                    .getBytes(StandardCharsets.UTF_8));
        }
        body.writeBytes(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"audio\"; filename=\"a.wav\"\r\n"
                + "Content-Type: audio/wav\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        body.writeBytes(audio);
        body.writeBytes(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
        return HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/attempts"))
                .header("Origin", TalkTestClient.ORIGIN).header("X-Bolo", "1").header("Cookie", cookie)
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArray(body.toByteArray())).build(), HttpResponse.BodyHandlers.ofString());
    }

    String learner() throws Exception {
        return new TalkTestClient(port).consentedGuest(5);
    }

    @Test
    void aClearAttemptIsNailedAndStored() throws Exception {
        String cookie = learner();
        HttpResponse<String> r = attempt(cookie, wav(), "It is very important to visit the village.");
        assertThat(r.statusCode()).as(r.body()).isEqualTo(200);
        assertThat((String) JsonPath.read(r.body(), "$.level")).isEqualTo("NAILED");
        assertThat(jdbc.sql("select count(*) from attempt").query(Integer.class).single()).isPositive();
    }

    @Test
    void anExtraVowelIsShownOnTheWord() throws Exception {
        vendor.next(new PronunciationResult(true, 100, List.of(
                new ScoredWord("small", 0, 300, 100, "None", List.of()), new ScoredWord("i", 300, 400, 100, "Insertion", List.of()),
                new ScoredWord("school", 400, 800, 100, "None", List.of()))));
        HttpResponse<String> r = attempt(learner(), wav(), "a small school");
        assertThat((String) JsonPath.read(r.body(), "$.focus[0].word")).isEqualTo("school");
        assertThat((String) JsonPath.read(r.body(), "$.focus[0].kind")).isEqualTo("EXTRA_VOWEL");
        assertThat((String) JsonPath.read(r.body(), "$.focus[0].title")).isEqualTo("The s at the start");
    }

    @Test
    void nothingHeardIsOnceMoreNotAnError() throws Exception {
        vendor.next(PronunciationResult.nothingHeard());
        HttpResponse<String> r = attempt(learner(), wav(), "Hello there.");
        assertThat(r.statusCode()).isEqualTo(200);
        assertThat((String) JsonPath.read(r.body(), "$.level")).isEqualTo("ONCE_MORE");
        assertThat((Boolean) JsonPath.read(r.body(), "$.heard")).isFalse();
    }

    @Test
    void notAWavOrTooLongOrTooManyWordsIsRefused() throws Exception {
        String cookie = learner();
        assertThat(attempt(cookie, "hello".getBytes(StandardCharsets.UTF_8), "Hello.").body()).contains("bad-audio");
        assertThat(attempt(cookie, Wav.wrap(new byte[32 * 16_000], 16000), "Hello.").body()).contains("bad-audio");
        assertThat(attempt(cookie, wav(), "one two three four five six seven eight nine ten eleven twelve thirteen fourteen "
                + "fifteen sixteen seventeen eighteen nineteen twenty twentyone twentytwo twentythree twentyfour twentyfive "
                + "twentysix twentyseven twentyeight twentynine thirty thirtyone").body()).contains("bad-reference");
    }

    @Test
    void tooManyAttemptsInAMinuteSlowDown() throws Exception {
        String cookie = learner();
        for (int i = 0; i < 5; i++) assertThat(attempt(cookie, wav(), "Hello.").statusCode()).isEqualTo(200);
        HttpResponse<String> r = attempt(cookie, wav(), "Hello.");
        assertThat(r.statusCode()).isEqualTo(429);
    }

    @Test
    void aBusyVendorSaysSo() throws Exception {
        vendor.failWith(new SpeechBusyException());
        HttpResponse<String> r = attempt(learner(), wav(), "Hello.");
        assertThat(r.statusCode()).isEqualTo(503);
        assertThat(r.body()).contains("busy");
    }

    @Test
    void thePracticeDeckListsSixSounds() throws Exception {
        String cookie = learner();
        HttpResponse<String> r = HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/practice"))
                .header("Cookie", cookie).GET().build(), HttpResponse.BodyHandlers.ofString());
        List<String> titles = JsonPath.read(r.body(), "$.patterns[*].title");
        assertThat(titles).hasSize(6).contains("The v sound", "The s at the start");
        List<String> sentences = JsonPath.read(r.body(), "$.patterns[0].sentences");
        assertThat(sentences).hasSize(3);
    }

    /** Review #3: a reading that runs to the 15 s limit (plus timer slack) is accepted. */
    @Test
    void aFullLengthReadingIsAccepted() throws Exception {
        assertThat(attempt(learner(), Wav.wrap(new byte[32 * 15_200], 16000), "Hello.").statusCode()).isEqualTo(200);
    }

    /** Review #1: mode=upgrade (Try it on a sentence upgrade) doesn't mark dropped words. */
    @Test
    void anUpgradeAttemptIgnoresDroppedWords() throws Exception {
        vendor.next(new PronunciationResult(true, 95, List.of(new ScoredWord("i", 0, 100, 95, "None", List.of()),
                new ScoredWord("a", 0, 0, 0, "Omission", List.of()), new ScoredWord("fixed", 100, 400, 95, "None", List.of()))));
        HttpResponse<String> r = attempt(learner(), wav(), "I fixed a bug", "upgrade");
        assertThat((String) JsonPath.read(r.body(), "$.level")).isEqualTo("NAILED");
    }

    /** Review #7: while a live talk holds the vendor's one recognition (F0), practice says busy instead of breaking it. */
    @Test
    void practiceWaitsWhileATalkIsLive() throws Exception {
        TalkTestClient client = new TalkTestClient(port);
        String talker = client.consentedGuest(5);
        jdbc.sql("update talk_session set ended_at = now(), end_reason = 'learner_ended' where ended_at is null").update();
        String talkId = TalkTestClient.id(client.startTalk(talker));
        jdbc.sql("update talk_session set attached_at = now() where id = ?::uuid").param(talkId).update();
        try {
            HttpResponse<String> r = attempt(learner(), wav(), "Hello.");
            assertThat(r.statusCode()).isEqualTo(503);
            assertThat(r.body()).contains("busy");
        } finally {
            jdbc.sql("update talk_session set ended_at = now(), end_reason = 'learner_ended' where ended_at is null").update();
        }
    }
}
