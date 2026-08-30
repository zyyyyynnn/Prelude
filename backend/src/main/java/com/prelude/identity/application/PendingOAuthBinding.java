package com.prelude.identity.application;

import java.io.Serializable;

/**
 * Minimal pending OAuth binding intent kept in the HTTP session between the
 * OAuth callback and the password re-authentication of the existing account.
 */
public record PendingOAuthBinding(
    String provider,
    String providerSubject,
    String verifiedEmail
) implements Serializable {
}
