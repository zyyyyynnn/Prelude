package com.prelude.llm.api;

/** Reads and updates the account's mutable model configuration. */
public interface ModelConfigurationPort {

    ModelConfigurationView currentConfiguration(Long accountId);

    ModelConfigurationView saveConfiguration(Long accountId, SaveConfigurationCommand command);
}
