@org.springframework.modulith.ApplicationModule(
    displayName = "Interview",
    allowedDependencies = {"activity", "assets::integration", "context", "identity::api", "jobs::integration", "llm::api", "resume::integration", "position::catalog"}
)
package com.prelude.interview;
