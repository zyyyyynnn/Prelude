@org.springframework.modulith.ApplicationModule(
    displayName = "Artifact",
    allowedDependencies = {"interview::integration", "jobs::integration", "llm::api", "activity", "assets::integration", "identity::api"}
)
package com.prelude.artifact;
