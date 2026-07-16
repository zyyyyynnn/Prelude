package com.interview.platform.llm;

import okhttp3.Dns;

import java.util.Set;

final class CustomLlmTestClients {

    private CustomLlmTestClients() {
    }

    static CustomLlmEgressPolicy localPolicy(int port) {
        return new CustomLlmEgressPolicy(true, true, Set.of(port), Dns.SYSTEM);
    }

    static CustomLlmHttpClient localClient(int port) {
        return new CustomLlmHttpClient(localPolicy(port));
    }
}
