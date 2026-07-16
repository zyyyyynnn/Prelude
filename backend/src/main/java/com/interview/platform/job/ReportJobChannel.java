package com.interview.platform.job;

public final class ReportJobChannel {

    public static final String EXCHANGE = "prelude.report.exchange";
    public static final String QUEUE = "prelude.report.generate.queue";
    public static final String ROUTING_KEY = "report.generate";

    private ReportJobChannel() {
    }
}
