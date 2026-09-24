package com.prelude.identity.api.port;

import com.prelude.identity.domain.Account;

public interface AccountRepository {

    Account findById(long accountId);

    Account findByUsername(String username);

    Account findByEmail(String email);

    boolean isUsernameTaken(String username);

    boolean isUsernameTakenByOther(long accountId, String username);

    /** Persists a new account and writes the generated identifier back. */
    void add(Account account);

    /**
     * Rewrites the profile columns only while the row still carries {@code expectedRevision},
     * so a concurrent save cannot silently overwrite a newer one. Returns the rows written.
     */
    int replaceProfile(Account account, long expectedRevision, String operationId);

    /**
     * Points only the avatar reference at {@code avatarUrl}, under the same revision guard,
     * so publishing a staged object never mutates the caller's account.
     */
    int replaceAvatar(String avatarUrl, Account account, String operationId);
}
