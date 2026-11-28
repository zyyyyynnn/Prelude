package com.prelude.interview.domain;

import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

public final class InterviewMessageLineage {

    private InterviewMessageLineage() {
    }

    public static <T> List<T> effective(
        List<T> messages,
        Function<T, Long> id,
        Function<T, String> role,
        Function<T, Long> supersededBy,
        Function<T, Long> replyTo
    ) {
        Set<Long> superseded = messages.stream()
            .filter(message -> supersededBy.apply(message) != null)
            .map(id)
            .collect(Collectors.toSet());
        return messages.stream()
            .filter(message -> supersededBy.apply(message) == null)
            .filter(message -> !"assistant".equals(role.apply(message))
                || replyTo.apply(message) == null
                || !superseded.contains(replyTo.apply(message)))
            .toList();
    }
}
