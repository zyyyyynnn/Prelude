package com.prelude.identity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("oauth_binding")
public class OAuthBinding {

    private Long id;
    private Long accountId;
    private String provider;
    private String providerSubject;
    private LocalDateTime createdAt;
}
