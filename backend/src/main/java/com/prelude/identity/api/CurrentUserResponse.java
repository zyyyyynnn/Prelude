package com.prelude.identity.api;

public record CurrentUserResponse(
    Long accountId,
    String username
) {
}
