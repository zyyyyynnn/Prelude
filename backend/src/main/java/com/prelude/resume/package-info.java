@org.springframework.modulith.ApplicationModule(
    displayName = "Resume",
    allowedDependencies = {"assets::integration", "documents::extraction", "identity::api", "llm::api"}
)
package com.prelude.resume;
