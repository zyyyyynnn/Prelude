package com.prelude.context;

import java.util.List;

public interface RetrievalSourcePort {

    List<String> loadDocuments(String scopeType, Long scopeId);
}
