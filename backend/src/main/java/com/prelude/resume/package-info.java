@org.springframework.modulith.ApplicationModule(
    displayName = "Resume",
    allowedDependencies = {"documents::extraction", "identity::api", "llm::api"}
)
package com.prelude.resume;
