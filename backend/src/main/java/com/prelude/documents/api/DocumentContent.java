package com.prelude.documents.api;

public record DocumentContent(Kind kind, String text) {

    public enum Kind {
        TEXT,
        IMAGE
    }
}
