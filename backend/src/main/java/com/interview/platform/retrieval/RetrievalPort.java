package com.interview.platform.retrieval;

import java.util.List;

public interface RetrievalPort {

    String SCOPE_SESSION = "session";
    String SCOPE_RESUME = "resume";

    void index(String scopeType, Long scopeId, List<String> documents);

    List<String> search(String scopeType, Long scopeId, String query, int topK);

    void invalidate(String scopeType, Long scopeId);
}
