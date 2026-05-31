package com.interview.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "app.demo")
public class DemoProperties {

    private boolean enabled = false;
    private int streamDelayMs = 18;
    private int chunkSize = 12;
}
