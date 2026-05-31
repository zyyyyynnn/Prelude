package com.interview.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class ResumeParseResult {

    private List<String> skills = new ArrayList<>();
    private List<ResumeProjectDto> projects = new ArrayList<>();
}
