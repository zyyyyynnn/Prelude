package com.prelude.assets.persistence;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Interview-context attachment metadata. The binary truth lives in the
 * referenced asset; this row never stores bytes.
 */
@Data
@TableName("attachment")
public class StoredAttachment {

    private Long id;
    private Long accountId;
    private Long assetId;
    private String fileName;
    private String extractedText;
    private String scopeType;
    private Long scopeId;
    private LocalDateTime createdAt;
}
