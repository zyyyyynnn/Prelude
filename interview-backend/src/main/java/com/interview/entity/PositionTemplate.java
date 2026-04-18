package com.interview.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("position_template")
public class PositionTemplate {

    private Long id;
    private String name;
    private String systemPrompt;
}
