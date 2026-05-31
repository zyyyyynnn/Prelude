package com.interview.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class InterviewChatRequest {

    @Size(max = 4000, message = "单次回答不能超过4000字符")
    private String content;
}
