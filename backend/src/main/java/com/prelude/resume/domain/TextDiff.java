package com.prelude.resume.domain;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Line counts for one block's text, measured rather than estimated: the workspace shows
 * these numbers as the diff chip on the step that wrote the block, so a reviewer decides
 * from what actually changed.
 */
public final class TextDiff {

    private TextDiff() {
    }

    /** Added and removed lines between two versions of the same text. */
    public record Count(int added, int removed) {
    }

    public static Count count(String before, String after) {
        Map<String, Integer> beforeLines = frequency(lines(before));
        Map<String, Integer> afterLines = frequency(lines(after));

        int added = 0;
        for (Map.Entry<String, Integer> entry : afterLines.entrySet()) {
            added += Math.max(0, entry.getValue() - beforeLines.getOrDefault(entry.getKey(), 0));
        }
        int removed = 0;
        for (Map.Entry<String, Integer> entry : beforeLines.entrySet()) {
            removed += Math.max(0, entry.getValue() - afterLines.getOrDefault(entry.getKey(), 0));
        }
        return new Count(added, removed);
    }

    /**
     * Repeated lines count repeatedly: a block that drops one of three identical bullets
     * lost a line, and a set would report the edit as no change at all.
     */
    private static Map<String, Integer> frequency(List<String> lines) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (String line : lines) {
            counts.merge(line, 1, Integer::sum);
        }
        return counts;
    }

    private static List<String> lines(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        return Arrays.stream(text.split("\\R"))
            .map(String::trim)
            .filter(line -> !line.isEmpty())
            .toList();
    }
}
