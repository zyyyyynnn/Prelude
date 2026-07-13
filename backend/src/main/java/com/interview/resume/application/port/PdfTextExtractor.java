package com.interview.resume.application.port;

public interface PdfTextExtractor {

    String extract(byte[] pdfBytes);
}
