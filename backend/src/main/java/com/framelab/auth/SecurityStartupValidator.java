package com.framelab.auth;

import java.nio.charset.StandardCharsets;
import java.util.Set;

import com.framelab.config.SecurityProperties;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

@Component
public class SecurityStartupValidator implements InitializingBean {

    private static final Set<String> DEMO_SECRETS = Set.of(
            "framelab-demo-secret-change-me-32bytes-minimum",
            "framelab-dev-secret-change-me-32bytes");

    private final SecurityProperties properties;
    private final Environment environment;

    public SecurityStartupValidator(SecurityProperties properties, Environment environment) {
        this.properties = properties;
        this.environment = environment;
    }

    @Override
    public void afterPropertiesSet() {
        String secret = properties.getJwtSecret();
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException("FRAMELAB_JWT_SECRET must be at least 32 bytes");
        }
        boolean demoProfile = environment.acceptsProfiles(Profiles.of("dev", "docker", "test"));
        if (!demoProfile && (DEMO_SECRETS.contains(secret) || secret.contains("change-me"))) {
            throw new IllegalStateException("FRAMELAB_JWT_SECRET must be replaced outside demo profiles");
        }
    }
}
