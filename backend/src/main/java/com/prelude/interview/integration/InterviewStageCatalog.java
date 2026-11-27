package com.prelude.interview.integration;

import com.prelude.interview.domain.InterviewStageDefinition;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

public final class InterviewStageCatalog {

    public static final List<StageDescription> STAGES = Arrays.stream(InterviewStageDefinition.values())
        .map(StageDescription::new)
        .toList();

    private InterviewStageCatalog() {
    }

    public static Optional<StageDescription> find(String key) {
        return STAGES.stream().filter(stage -> stage.key().equals(key)).findFirst();
    }

    public static StageDescription require(String key) {
        return find(key).orElseThrow(() -> new IllegalArgumentException("Unknown interview stage: " + key));
    }

    public record StageDescription(String key, String wireName, int replyQuota, int order) {
        StageDescription(InterviewStageDefinition definition) {
            this(definition.key(), definition.key(), definition.replyQuota(), definition.ordinal());
        }
    }
}
