package com.prelude.assets.persistence;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("attachment")
public class StoredAttachment {

    private Long id;
    private Long userId;
    private String fileName;
    private String mediaType;
    private Long byteSize;
    private Integer image;
    private String extractedText;
    private byte[] content;
    private String scopeType;
    private Long scopeId;
    private LocalDateTime createdAt;
}
