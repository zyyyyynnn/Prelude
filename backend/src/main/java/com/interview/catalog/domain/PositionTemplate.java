package com.interview.catalog.domain;

import lombok.Data;

@Data
public class PositionTemplate {

    private Long id;
    private String name;
    private String systemPrompt;
}
