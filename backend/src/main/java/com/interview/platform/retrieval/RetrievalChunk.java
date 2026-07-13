package com.interview.platform.retrieval;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("retrieval_chunk")
public class RetrievalChunk {

    private Long id;
    private String scopeType;
    private Long scopeId;
    private Integer ordinal;
    private String content;
    private String contentHash;
}
