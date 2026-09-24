package com.prelude.jobs;

import java.util.regex.Pattern;

/**
 * Redacts credentials and control characters from job failure summaries
 * before they are persisted or published.
 */
final class JobFailureRedaction {

    private static final int MAX_SUMMARY_LENGTH = 1024;
    private static final Pattern BEARER_SECRET = Pattern.compile("(?i)(bearer\\s+)[^\\s,;]+");
    private static final Pattern NAMED_SECRET = Pattern.compile(
        "(?i)((?:api[-_ ]?key|authorization|token|secret)\\s*[:=]\\s*)[^\\s,;]+"
    );
    private static final Pattern OPENAI_STYLE_SECRET = Pattern.compile("\\bsk-[A-Za-z0-9_-]{8,}\\b");
    private static final Pattern URL_CREDENTIALS = Pattern.compile("(?i)(https?://)[^\\s/@]+@");
    private static final Pattern URL_QUERY = Pattern.compile("(?i)(https?://[^\\s?#]+)\\?[^\\s]+");

    private JobFailureRedaction() {
    }

    static String sanitize(Throwable failure) {
        String message = failure == null || failure.getMessage() == null || failure.getMessage().isBlank()
            ? failure == null ? "Unknown failure" : failure.getClass().getSimpleName()
            : failure.getMessage();
        String safe = message.replaceAll("[\\r\\n\\t]+", " ").replaceAll("\\p{Cntrl}", " ");
        safe = URL_CREDENTIALS.matcher(safe).replaceAll("$1[REDACTED]@");
        safe = URL_QUERY.matcher(safe).replaceAll("$1?[REDACTED]");
        safe = BEARER_SECRET.matcher(safe).replaceAll("$1[REDACTED]");
        safe = NAMED_SECRET.matcher(safe).replaceAll("$1[REDACTED]");
        safe = OPENAI_STYLE_SECRET.matcher(safe).replaceAll("[REDACTED]");
        return safe.length() <= MAX_SUMMARY_LENGTH
            ? safe
            : safe.substring(0, MAX_SUMMARY_LENGTH);
    }
}
