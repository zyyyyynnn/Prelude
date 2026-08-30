package com.prelude.template.domain;

import lombok.Data;

@Data
public class PositionTemplate {

    private Long id;
    private Long accountId;
    private String name;
    private String systemPrompt;
}
