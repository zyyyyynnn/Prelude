package com.interview.util;

import java.util.ArrayList;
import java.util.List;

public class InMemoryVectorIndex {

    public record Entry(String text, float[] vector) {}

    private final List<Entry> entries = new ArrayList<>();

    public void add(String text, float[] vector) {
        entries.add(new Entry(text, vector));
    }

    public void clear() {
        entries.clear();
    }

    public List<Entry> search(float[] queryVector, int topK) {
        List<EntryScore> scored = new ArrayList<>();
        for (Entry entry : entries) {
            double sim = cosineSimilarity(queryVector, entry.vector());
            scored.add(new EntryScore(entry, sim));
        }
        scored.sort((a, b) -> Double.compare(b.score, a.score));
        List<Entry> result = new ArrayList<>();
        for (int i = 0; i < Math.min(topK, scored.size()); i++) {
            result.add(scored.get(i).entry);
        }
        return result;
    }

    private double cosineSimilarity(float[] v1, float[] v2) {
        double dotProduct = 0.0;
        double normA = 0.0;
        double normB = 0.0;
        for (int i = 0; i < Math.min(v1.length, v2.length); i++) {
            dotProduct += v1[i] * v2[i];
            normA += Math.pow(v1[i], 2);
            normB += Math.pow(v2[i], 2);
        }
        if (normA == 0 || normB == 0) {
            return 0.0;
        }
        return dotProduct / (Math.sqrt(normA) * Math.sqrt(normB));
    }

    private record EntryScore(Entry entry, double score) {}
}
