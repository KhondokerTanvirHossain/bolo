package app.bolo.talk;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.net.URI;
import java.net.http.HttpResponse;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Founder 2026-11-18: the deployed Bolo understands Bangla. A new guest says one Bangla sentence (an Azure bn-BD
 * synthetic voice, not a real speaker); the turn must come back in Bangla script and Bolo must answer in English.
 * Run with scripts/talk-probe.sh LiveBanglaProbe.
 */
@Tag("live")
@EnabledIfEnvironmentVariable(named = "BOLO_LIVE_URL", matches = "https://.+")
class LiveBanglaProbe {

    @Test
    void aBanglaTurnIsHeardInBangla() throws Exception {
        LiveTalkProbe p = new LiveTalkProbe();
        String url = LiveTalkProbe.URL;
        String cookie = p.post("/api/session", null, LiveTalkProbe.sessionBody()).headers().firstValue("set-cookie").orElseThrow().split(";", 2)[0];
        p.post("/api/consent", cookie, "{\"keepRecordings\":false,\"over13\":true,\"policyVersion\":\"2026-11-v1\"}");
        p.send("PUT", "/api/profile", cookie, "{\"goal\":\"work_meetings\",\"fearedMoment\":\"speaking_up\",\"talkMinutes\":5}");
        HttpResponse<String> talk = p.post("/api/talk", cookie, "{}");
        assertThat(talk.statusCode()).as("POST /api/talk: %s", talk.body()).isEqualTo(201);
        String id = LiveTalkProbe.field(talk.body(), "id");
        WebSocket ws = p.http.newWebSocketBuilder().header("Cookie", cookie).header("Origin", url)
                .buildAsync(URI.create(url.replaceFirst("^https", "wss") + "/ws/talk/" + id), p.new Collector()).get(15, TimeUnit.SECONDS);
        try {
            p.awaitText(25_000, "\"buddy_done\"", new StringBuilder(), 0);
            ws.sendText("{\"type\":\"played\",\"turn\":0}", true).join();
            p.awaitText(5_000, "\"your_turn\"", null, -1);
            byte[] pcm = app.bolo.WavFixture.pcm("/audio/bangla-boss-tts-16k.wav");
            long next = System.nanoTime();
            for (int i = 0; i + 640 <= pcm.length; i += 640) {
                ws.sendBinary(ByteBuffer.wrap(Arrays.copyOfRange(pcm, i, i + 640)), true).join();
                next += 20_000_000;
                LiveTalkProbe.sleepUntil(next);
            }
            String turnEnd = null;
            long silenceStart = System.nanoTime();
            for (int i = 0; i < 500 && turnEnd == null; i++) {
                ws.sendBinary(ByteBuffer.wrap(new byte[640]), true).join();
                next += 20_000_000;
                LiveTalkProbe.sleepUntil(next);
                turnEnd = p.inbox.stream().filter(m -> m instanceof String s && s.contains("\"turn_end\"")).map(String.class::cast).findFirst().orElse(null);
            }
            assertThat(turnEnd).as("turn_end within 10 s of silence").isNotNull();
            String said = LiveTalkProbe.field(turnEnd, "text");
            System.out.println("turn text: " + said + " (" + (System.nanoTime() - silenceStart) / 1_000_000 + " ms after the speech)");
            StringBuilder reply = new StringBuilder();
            p.awaitText(25_000, "\"buddy_done\"", reply, 2);
            System.out.println("reply caption: " + reply.toString().trim());
            assertThat(app.bolo.common.text.Bangla.is(said)).as("the turn in Bangla script: %s", said).isTrue();
            assertThat(reply.toString()).as("Bolo answers in English").containsIgnoringCase("in english");
            ws.sendText("{\"type\":\"end\"}", true).join();
        } finally {
            ws.abort();
        }
    }
}
