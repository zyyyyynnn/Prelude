package com.prelude.template.api;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class PositionTemplateResponse {

    private Long id;
    private String name;
    private String systemPrompt;
    private Boolean editable;
}
