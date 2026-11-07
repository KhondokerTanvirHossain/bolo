package app.bolo.talk;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

/** Plain HTTP against a running test server: a guest with consent (and optionally a profile), then talks. */
public final class TalkTestClient {

    public static final String ORIGIN = "http://localhost:3000";

    private final HttpClient http = HttpClient.newHttpClient();
    private final String base;

    public TalkTestClient(int port) {
        this.base = "http://localhost:" + port;
    }

    public record Reply(int status, String body, String cookie) {
    }

    public Reply send(String method, String path, String cookie, String json) throws IOException, InterruptedException {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(base + path))
                .header("Origin", ORIGIN).header("X-Bolo", "1").header("Content-Type", "application/json")
                .method(method, HttpRequest.BodyPublishers.ofString(json));
        if (cookie != null) b.header("Cookie", cookie);
        HttpResponse<String> res = http.send(b.build(), HttpResponse.BodyHandlers.ofString());
        String setCookie = res.headers().firstValue("set-cookie").map(v -> v.split(";", 2)[0]).orElse(cookie);
        return new Reply(res.statusCode(), res.body(), setCookie);
    }

    /** A new guest: returns the "name=value" cookie. */
    public String guest() throws IOException, InterruptedException {
        return send("POST", "/api/session", null, "{}").cookie();
    }

    public String consentedGuest(Integer talkMinutes) throws IOException, InterruptedException {
        String cookie = guest();
        send("POST", "/api/consent", cookie, "{\"keepRecordings\":true,\"over13\":true,\"policyVersion\":\"2026-11-v1\"}");
        if (talkMinutes != null) {
            send("PUT", "/api/profile", cookie, "{\"goal\":\"job_interviews\",\"fearedMoment\":\"interviews\",\"talkMinutes\":"
                    + talkMinutes + "}");
        }
        return cookie;
    }

    public Reply startTalk(String cookie) throws IOException, InterruptedException {
        return send("POST", "/api/talk", cookie, "{}");
    }

    public static String id(Reply talk) {
        return com.jayway.jsonpath.JsonPath.read(talk.body(), "$.id");
    }
}
