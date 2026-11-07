package app.bolo.coaching;

import app.bolo.WavFixture;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Coach v1 against the deployed Bolo: four spoken turns (about 72 s, with "bhery"/"bhisit" and simple grammar slips)
 * through real Azure + Claude, then the debrief. Prints the time to ready, the strength and each fix.
 * Run with scripts/coach-probe.sh.
 */
@Tag("live")
@EnabledIfEnvironmentVariable(named = "BOLO_LIVE_URL", matches = "https://.+")
class LiveCoachProbe {

    static final String URL = System.getenv("BOLO_LIVE_URL");
    final HttpClient http = HttpClient.newHttpClient();
    final BlockingQueue<String> inbox = new LinkedBlockingQueue<>();

    @Test
    void aRealTalkGetsADebrief() throws Exception {
        String cookie = send("POST", "/api/session", null, "{}").headers().firstValue("set-cookie").orElseThrow().split(";", 2)[0];
        send("POST", "/api/consent", cookie, "{\"keepRecordings\":false,\"over13\":true,\"policyVersion\":\"2026-11-v1\"}");
        send("PUT", "/api/profile", cookie, "{\"goal\":\"job_interviews\",\"fearedMoment\":\"interviews\",\"talkMinutes\":5}");
        HttpResponse<String> talk = send("POST", "/api/talk", cookie, "{}");
        assertThat(talk.statusCode()).as(talk.body()).isEqualTo(201);
        String id = field(talk.body(), "id");
        WebSocket ws = http.newWebSocketBuilder().header("Cookie", cookie).header("Origin", URL)
                .buildAsync(URI.create(URL.replaceFirst("^https", "wss") + "/ws/talk/" + id), new Texts()).get(15, TimeUnit.SECONDS);
        try {
            int buddy = 0;
            await(25_000, "\"buddy_done\"");
            for (int t = 1; t <= 4; t++) {
                ws.sendText("{\"type\":\"played\",\"turn\":" + buddy + "}", true).join();
                await(5_000, "\"your_turn\"");
                byte[] pcm = WavFixture.pcm("/audio/coach-turn-" + t + "-16k.wav");
                long next = System.nanoTime();
                for (int i = 0; i + 640 <= pcm.length; i += 640) {
                    ws.sendBinary(ByteBuffer.wrap(Arrays.copyOfRange(pcm, i, i + 640)), true).join();
                    next += 20_000_000;
                    sleepUntil(next);
                }
                boolean ended = false;
                for (int i = 0; i < 400 && !ended; i++) {
                    ws.sendBinary(ByteBuffer.wrap(new byte[640]), true).join();
                    next += 20_000_000;
                    sleepUntil(next);
                    ended = inbox.stream().anyMatch(m -> m.contains("\"turn_end\""));
                }
                await(5_000, "\"turn_end\"");
                buddy += 2;
                await(25_000, "\"buddy_done\"");
                System.out.println("turn " + t + " done");
            }
            ws.sendText("{\"type\":\"end\"}", true).join();
            await(5_000, "\"ended\"");
        } finally {
            ws.abort();
        }
        long endedAt = System.currentTimeMillis();
        String body = "";
        while (System.currentTimeMillis() - endedAt < 60_000) {
            body = send("GET", "/api/talk/" + id + "/debrief", cookie, null).body();
            if (!body.matches("(?s).*\"status\"\\s*:\\s*\"scoring\".*")) break;
            Thread.sleep(1000);
        }
        System.out.println("debrief after " + (System.currentTimeMillis() - endedAt) + " ms: " + body);
        assertThat(body).containsPattern("\"status\"\\s*:\\s*\"ready\"");
    }

    void await(long ms, String fragment) throws InterruptedException {
        long until = System.currentTimeMillis() + ms;
        while (System.currentTimeMillis() < until) {
            String m = inbox.poll(until - System.currentTimeMillis(), TimeUnit.MILLISECONDS);
            if (m == null) break;
            if (m.contains("\"ended\"") && !fragment.equals("\"ended\"")) throw new AssertionError("talk ended: " + m);
            if (m.contains(fragment)) return;
        }
        throw new AssertionError("no " + fragment + " within " + ms + " ms");
    }

    HttpResponse<String> send(String method, String path, String cookie, String json) throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(URL + path)).header("Origin", URL);
        if (json != null) b.header("X-Bolo", "1").header("Content-Type", "application/json").method(method, HttpRequest.BodyPublishers.ofString(json));
        else b.GET();
        if (cookie != null) b.header("Cookie", cookie);
        return http.send(b.build(), HttpResponse.BodyHandlers.ofString());
    }

    static String field(String json, String name) {
        Matcher m = Pattern.compile("\"" + name + "\":\"([^\"]*)\"").matcher(json);
        return m.find() ? m.group(1) : "";
    }

    static void sleepUntil(long nanos) throws InterruptedException {
        long wait = nanos - System.nanoTime();
        if (wait > 0) Thread.sleep(wait / 1_000_000, (int) (wait % 1_000_000));
    }

    final class Texts implements WebSocket.Listener {
        private final StringBuilder text = new StringBuilder();

        @Override
        public CompletionStage<?> onText(WebSocket ws, CharSequence data, boolean last) {
            text.append(data);
            if (last) {
                inbox.add(text.toString());
                text.setLength(0);
            }
            ws.request(1);
            return null;
        }

        @Override
        public CompletionStage<?> onBinary(WebSocket ws, ByteBuffer data, boolean last) {
            ws.request(1);
            return null;
        }
    }
}
