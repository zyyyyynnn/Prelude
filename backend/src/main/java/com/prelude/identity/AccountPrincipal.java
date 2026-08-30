package com.prelude.identity;

import java.io.Serializable;
import java.security.Principal;

/**
 * Serializable session principal. The name is the stable account id used
 * by Spring Session principal indexing and the WebSocket handshake.
 */
public record AccountPrincipal(Long accountId, String username) implements Principal, Serializable {

    @Override
    public String getName() {
        return String.valueOf(accountId);
    }
}
