@org.springframework.modulith.ApplicationModule(
    displayName = "Interview",
    allowedDependencies = {"activity", "assets::integration", "context", "identity::api", "jobs::integration", "llm::api", "resume::integration", "template::catalog"}
)
package com.prelude.interview;
