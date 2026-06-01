package com.interview.util;

import java.util.ArrayList;
import java.util.List;

public class TextSplitter {

    public static List<String> splitText(String text, int size, int overlap) {
        List<String> chunks = new ArrayList<>();
        if (text == null || text.isBlank()) {
            return chunks;
        }
        int start = 0;
        while (start < text.length()) {
            int end = Math.min(start + size, text.length());
            chunks.add(text.substring(start, end));
            if (end == text.length()) {
                break;
            }
            start = end - overlap;
            if (start < 0 || start >= text.length()) {
                break;
            }
        }
        return chunks;
    }
}
