package app.bolo.learner.config;

import app.bolo.learner.adapter.out.fake.FakeIdentity;
import app.bolo.learner.adapter.out.firebase.FirebaseIdTokens;
import app.bolo.learner.application.port.in.SignInConfig;
import app.bolo.learner.application.port.out.IdentityPort;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.util.Optional;

/** The identity vendor is a property (bolo.auth.vendor: firebase | fake). Without Firebase's config, sign-in is off. */
@Configuration
class IdentityConfiguration {

    @Bean
    SignInConfig signInConfig(@Value("${bolo.firebase.api-key:}") String apiKey, @Value("${bolo.firebase.auth-domain:}") String authDomain,
                              @Value("${bolo.firebase.project-id:}") String projectId, @Value("${bolo.firebase.app-id:}") String appId) {
        return new SignInConfig(apiKey, authDomain, projectId, appId);
    }

    @Bean
    @ConditionalOnProperty(name = "bolo.auth.vendor", havingValue = "firebase")
    IdentityPort firebaseIdentity(SignInConfig config) {
        return config.enabled() ? FirebaseIdTokens.forGoogle(config.projectId(), Clock.systemUTC()) : token -> Optional.empty();
    }

    @Bean
    @ConditionalOnProperty(name = "bolo.auth.vendor", havingValue = "fake", matchIfMissing = true)
    IdentityPort fakeIdentity() {
        return new FakeIdentity();
    }
}
