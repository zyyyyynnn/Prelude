package com.prelude.resume.api;

import jakarta.validation.constraints.NotBlank;

public record ResumeInstructionRequest(@NotBlank String instruction) {
}
