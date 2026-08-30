package com.prelude.assets.api;

/**
 * Public reference to a stored asset. The id is the only cross-module handle;
 * object keys and storage details stay inside the assets module.
 */
public record AssetRef(Long id) {
}
