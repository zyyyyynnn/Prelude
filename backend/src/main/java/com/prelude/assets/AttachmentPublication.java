package com.prelude.assets;

import com.prelude.BusinessException;
import com.prelude.assets.application.port.AssetLookup.AssetRow;
import com.prelude.assets.application.port.AttachmentStorage;
import com.prelude.assets.application.port.AttachmentStorage.AttachmentRow;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Transaction-boundary collaborator for attachment publication: the business
 * reference and the PENDING_UPLOAD → READY transition commit together, after
 * the remote object already exists. Any failure rolls both back, leaving the
 * asset PENDING for the reconciler.
 */
@Component
@RequiredArgsConstructor
public class AttachmentPublication {

    private final AttachmentStorage attachmentStorage;
    private final AssetService assetService;

    @Transactional(rollbackFor = Exception.class)
    public AttachmentRow finalizeUpload(AssetRow asset, AttachmentRow stored) {
        AttachmentRow persisted = attachmentStorage.insert(stored);
        if (!assetService.markReady(asset.id())) {
            throw BusinessException.badRequest("附件上传失败");
        }
        return persisted;
    }
}
