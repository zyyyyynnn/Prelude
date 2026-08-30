package com.prelude.assets.persistence;

import com.baomidou.mybatisplus.annotation.TableName;
import com.prelude.assets.domain.AssetStatus;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("asset")
public class Asset {

    private Long id;
    private Long accountId;
    private String kind;
    private String objectKey;
    private String mediaType;
    private Long byteSize;
    private Integer width;
    private Integer height;
    private AssetStatus status;
    private LocalDateTime createdAt;
}
