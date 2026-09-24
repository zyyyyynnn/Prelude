package com.prelude.position.domain;

import lombok.Data;

@Data
public class Position {

    private Long id;
    private Long accountId;
    private String name;
    private String systemPrompt;
}
