package com.prelude.position.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@TableName("position_template")
@Data
public class Position {

    private Long id;
    private Long accountId;
    private String name;
    private String systemPrompt;
}
