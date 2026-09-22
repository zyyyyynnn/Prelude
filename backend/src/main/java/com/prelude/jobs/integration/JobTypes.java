package com.prelude.jobs.integration;

/**
 * The job types this system dispatches. Exported so every module that enqueues a job
 * or reacts to its terminal state names the same string instead of restating the
 * literal, which is what let the value drift between the four places that used it.
 */
public final class JobTypes {

    /** Report generation for an interview session that reached its closing stage. */
    public static final String REPORT_GENERATE = "report.generate";

    private JobTypes() {
    }
}
