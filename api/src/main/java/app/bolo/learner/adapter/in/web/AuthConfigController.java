package app.bolo.learner.adapter.in.web;

import app.bolo.learner.application.port.in.SignInConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/** Public: Firebase's web config (not secret), read at runtime so sign-in can be switched on without a rebuild. */
@RestController
@RequiredArgsConstructor
class AuthConfigController {

    private final SignInConfig config;

    @GetMapping("/api/auth/config")
    Map<String, Object> config() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("enabled", config.enabled());
        if (config.enabled()) {
            m.put("apiKey", config.apiKey());
            m.put("authDomain", config.authDomain());
            m.put("projectId", config.projectId());
            m.put("appId", config.appId());
        }
        return m;
    }
}
