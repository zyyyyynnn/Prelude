package com.interview.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class InterviewChatRequest {

    @NotBlank(message = "回答内容不能为空")
    @Size(max = 4000, message = "单次回答不能超过4000字符")
    private String content;
}
