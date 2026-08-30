@org.springframework.modulith.ApplicationModule(
    displayName = "Interview",
    allowedDependencies = {"activity", "assets::integration", "context", "identity::api", "jobs", "llm", "resume::integration", "template::catalog"}
)
package com.prelude.interview;
