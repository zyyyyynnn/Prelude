package com.interview.service;

import java.util.List;

public interface SessionRagService {
    void indexSession(Long sessionId, String resumeText, String jdText);
    List<String> searchTopChunks(Long sessionId, String query, int topK);
}
