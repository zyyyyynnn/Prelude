package com.prelude.interview.application;

import com.prelude.assets.api.AttachmentSnapshot;
import com.prelude.resume.api.port.ResumeProjection;
import java.util.ArrayList;
import java.util.List;

public final class InterviewRetrievalDocuments {

    private InterviewRetrievalDocuments() {
    }

    public static List<String> from(
        ResumeProjection resume,
        String jdText,
        List<AttachmentSnapshot> attachments
    ) {
        List<String> documents = new ArrayList<>();
        addIfPresent(documents, resume.plainText());
        addIfPresent(documents, jdText);
        if (attachments != null) {
            attachments.stream().map(AttachmentSnapshot::text).forEach(text -> addIfPresent(documents, text));
        }
        return List.copyOf(documents);
    }

    private static void addIfPresent(List<String> documents, String value) {
        if (value != null && !value.isBlank()) {
            documents.add(value);
        }
    }
}
