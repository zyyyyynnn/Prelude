package com.prelude.assets.api;

import com.prelude.assets.AssetService;
import com.prelude.assets.persistence.Asset;
import com.prelude.identity.api.CurrentAccount;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

/**
 * Authorized download flow: authenticate, resolve the owned READY asset,
 * then issue a short-TTL single-object presigned URL.
 */
@RestController
@RequestMapping("/api/assets")
@RequiredArgsConstructor
public class AssetController {

    private final CurrentAccount currentAccount;
    private final AssetService assetService;

    @GetMapping("/{assetId}/content")
    public ResponseEntity<Void> content(@PathVariable Long assetId) {
        long accountId = currentAccount.requireId();
        Asset asset = assetService.requireOwnedReady(accountId, assetId);
        String url = assetService.presignGet(asset);
        return ResponseEntity.status(HttpStatus.FOUND)
            .location(URI.create(url))
            .build();
    }
}
