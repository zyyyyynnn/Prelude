package com.prelude.identity.domain;

import lombok.Data;

@Data
public class OAuthBinding {

    private Long id;
    private Long accountId;
    private String provider;
    private String providerSubject;
}
