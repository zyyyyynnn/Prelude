package com.prelude.assets;

import com.prelude.BusinessException;
import com.prelude.assets.persistence.Asset;
import com.prelude.assets.persistence.StoredAttachment;
import com.prelude.assets.persistence.AttachmentMapper;
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

    private final AttachmentMapper attachmentMapper;
    private final AssetService assetService;

    @Transactional(rollbackFor = Exception.class)
    public void finalizeUpload(Asset asset, StoredAttachment stored) {
        attachmentMapper.insert(stored);
        if (!assetService.markReady(asset.getId())) {
            throw BusinessException.badRequest("附件上传失败");
        }
    }
}
