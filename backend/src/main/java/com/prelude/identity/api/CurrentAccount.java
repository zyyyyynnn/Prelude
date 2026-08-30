package com.prelude.identity.api;

/**
 * The authenticated account principal of the current Spring Security context.
 */
public interface CurrentAccount {

    Long idOrNull();

    long requireId();
}
