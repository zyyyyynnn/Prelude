package com.interview.insight.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("user_weakness")
public class UserWeakness {

    private Long id;
    private Long userId;
    private Long sessionId;
    private String category;
    private String description;
    private LocalDateTime createdAt;
}
