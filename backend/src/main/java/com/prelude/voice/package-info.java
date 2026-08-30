@org.springframework.modulith.ApplicationModule(
    displayName = "Voice",
    allowedDependencies = {"interview::integration", "identity::api", "llm"}
)
package com.prelude.voice;
