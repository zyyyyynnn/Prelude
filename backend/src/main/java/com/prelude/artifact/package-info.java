@org.springframework.modulith.ApplicationModule(
    displayName = "Artifact",
    allowedDependencies = {"interview::integration", "jobs", "llm", "activity", "resume::integration"}
)
package com.prelude.artifact;
