package com.interview.platform.llm;

public final class PromptVersions {

    public static final String V1 = "v1";
    public static final String CHAT = "interview.chat";
    public static final String JUDGE = "interview.judge";
    public static final String REPORT = "interview.report";
    public static final String SUMMARY = "interview.summary";
    public static final String RESUME_PARSE = "resume.parse";
    public static final String DEFAULT_SNAPSHOT_JSON =
        "{\"chat\":\"v1\",\"judge\":\"v1\",\"report\":\"v1\",\"parse\":\"v1\"}";

    private PromptVersions() {
    }
}
