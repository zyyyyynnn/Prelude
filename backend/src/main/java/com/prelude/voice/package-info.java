@org.springframework.modulith.ApplicationModule(
    displayName = "Voice",
    allowedDependencies = {"interview::integration", "identity::api", "identity::security", "llm::api"}
)
package com.prelude.voice;
