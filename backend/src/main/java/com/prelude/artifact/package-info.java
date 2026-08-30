@org.springframework.modulith.ApplicationModule(
    displayName = "Artifact",
    allowedDependencies = {"interview::integration", "jobs", "llm", "activity", "assets::integration", "identity::api"}
)
package com.prelude.artifact;
