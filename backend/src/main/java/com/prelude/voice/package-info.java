@org.springframework.modulith.ApplicationModule(
    displayName = "Voice",
    allowedDependencies = {"interview::integration", "identity::api", "llm::api"}
)
package com.prelude.voice;
