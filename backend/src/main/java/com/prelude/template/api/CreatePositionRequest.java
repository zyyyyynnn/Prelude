package com.prelude.template.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreatePositionRequest(
    @NotBlank(message = "岗位名称不能为空")
    @Size(max = 100, message = "岗位名称不能超过 100 个字符")
    String name,

    @NotBlank(message = "面试侧重点不能为空")
    @Size(max = 4000, message = "面试侧重点不能超过 4000 个字符")
    String systemPrompt
) {
}
