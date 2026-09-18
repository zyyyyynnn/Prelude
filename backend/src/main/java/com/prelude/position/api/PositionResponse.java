package com.prelude.position.api;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class PositionResponse {

    private Long id;
    private String name;
    private String systemPrompt;
    private Boolean editable;
}
