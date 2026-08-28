package com.prelude.documents.api;

public interface DocumentExtractor {

    DocumentContent extract(String fileName, String mediaType, byte[] content);
}
