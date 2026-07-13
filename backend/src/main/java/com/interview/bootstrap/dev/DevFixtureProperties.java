package com.interview.bootstrap.dev;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "app.dev-fixtures")
public class DevFixtureProperties {

    private boolean enabled = false;
    private int streamDelayMs = 18;
    private int chunkSize = 12;
}
