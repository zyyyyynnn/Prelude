package com.interview.resume.application;

public record ResumeMigrationReport(
    long total,
    long succeeded,
    long failed,
    long skipped
) {
    public double successRate() {
        return total == 0 ? 1.0 : (double) (succeeded + skipped) / total;
    }
}
