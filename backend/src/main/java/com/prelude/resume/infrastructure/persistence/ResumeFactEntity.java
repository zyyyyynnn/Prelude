package com.prelude.resume.infrastructure.persistence;

import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("resume_fact")
public class ResumeFactEntity {

    private Long id;
    private Long resumeId;
    private String blockId;
    private String value;
    private String verificationStatus;
    private String evidenceJson;
    private LocalDateTime updatedAt;
}
