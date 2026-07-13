package com.interview.platform.retrieval;

import java.util.List;

public interface RetrievalSourcePort {

    List<String> loadDocuments(String scopeType, Long scopeId);
}
