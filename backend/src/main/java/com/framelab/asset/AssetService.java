package com.framelab.asset;

import java.io.IOException;
import java.util.List;
import java.util.regex.Pattern;

import com.framelab.asset.dto.AssetView;
import com.framelab.common.error.ApiException;
import com.framelab.common.error.ErrorCode;
import com.framelab.common.error.FieldErrorView;
import com.framelab.common.i18n.MessageResolver;
import com.framelab.common.util.Uuids;
import com.framelab.config.StorageProperties;
import com.framelab.infrastructure.storage.ObjectStorage;
import com.framelab.infrastructure.storage.StorageException;
import com.framelab.infrastructure.storage.StoredObject;
import com.framelab.template.TemplateLimits;
import com.framelab.template.TemplateService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class AssetService {

    private static final Logger log = LoggerFactory.getLogger(AssetService.class);
    private static final Pattern TEMPLATE_ID = Pattern.compile("[a-z0-9]+(?:-[a-z0-9]+)*");
    private static final List<String> DEFAULT_MIME_TYPES = List.of("image/jpeg", "image/png");

    private final AssetMapper assetMapper;
    private final TemplateService templateService;
    private final ImageNormalizer imageNormalizer;
    private final ObjectStorage objectStorage;
    private final StorageProperties storageProperties;
    private final MessageResolver messages;

    public AssetService(AssetMapper assetMapper,
                        TemplateService templateService,
                        ImageNormalizer imageNormalizer,
                        ObjectStorage objectStorage,
                        StorageProperties storageProperties,
                        MessageResolver messages) {
        this.assetMapper = assetMapper;
        this.templateService = templateService;
        this.imageNormalizer = imageNormalizer;
        this.objectStorage = objectStorage;
        this.storageProperties = storageProperties;
        this.messages = messages;
    }

    public AssetView upload(long userId, MultipartFile file, String templateId) {
        TemplateLimits limits = limitsFor(templateId);
        if (file == null || file.isEmpty()) {
            throw ApiException.validation(List.of(new FieldErrorView("file", messages.get("validation.file.required"))));
        }
        if (file.getSize() > limits.maxFileSizeBytes()) {
            throw ApiException.of(ErrorCode.FILE_TOO_LARGE);
        }
        byte[] source;
        try {
            source = file.getBytes();
        } catch (IOException ex) {
            throw ApiException.of(ErrorCode.INTERNAL_ERROR);
        }
        NormalizedImage normalized = imageNormalizer.normalize(source, file.getContentType(),
                limits.acceptedMimeTypes());

        String assetId = Uuids.newId();
        StoredObject stored;
        try {
            stored = objectStorage.writeMaterial(userId, assetId, normalized.bytes(), normalized.extension());
        } catch (StorageException ex) {
            log.error("asset.store_failed userId={} assetId={}", userId, assetId, ex);
            throw ApiException.of(ErrorCode.SERVICE_UNAVAILABLE);
        }

        UploadAsset asset = new UploadAsset();
        asset.setId(assetId);
        asset.setUserId(userId);
        asset.setStorageKey(stored.storageKey());
        asset.setMimeType(normalized.mimeType());
        asset.setSizeBytes(normalized.bytes().length);
        asset.setWidth(normalized.width());
        asset.setHeight(normalized.height());
        try {
            assetMapper.insert(asset);
        } catch (RuntimeException ex) {
            deleteFileQuietly(stored.storageKey());
            throw ex;
        }

        UploadAsset persisted = assetMapper.findOwned(assetId, userId);
        if (persisted == null) {
            throw ApiException.of(ErrorCode.INTERNAL_ERROR);
        }
        log.info("asset.uploaded userId={} assetId={} bytes={}", userId, assetId, persisted.getSizeBytes());
        return AssetView.from(persisted);
    }

    public AssetContent content(long userId, String assetId) {
        UploadAsset asset = requireOwned(userId, assetId);
        try {
            return new AssetContent(asset, objectStorage.open(asset.getStorageKey()));
        } catch (StorageException ex) {
            log.error("asset.open_failed userId={} assetId={}", userId, assetId, ex);
            throw ApiException.of(ErrorCode.SERVICE_UNAVAILABLE);
        }
    }

    public void delete(long userId, String assetId) {
        UploadAsset asset = requireOwned(userId, assetId);
        if (assetMapper.countTaskReferences(assetId) > 0) {
            throw ApiException.of(ErrorCode.ASSET_IN_USE);
        }
        try {
            assetMapper.deleteOwned(assetId, userId);
        } catch (DataIntegrityViolationException ex) {
            throw ApiException.of(ErrorCode.ASSET_IN_USE);
        }
        deleteFileQuietly(asset.getStorageKey());
        log.info("asset.deleted userId={} assetId={}", userId, assetId);
    }

    public UploadAsset requireOwned(long userId, String assetId) {
        UploadAsset asset = assetMapper.findOwned(assetId, userId);
        if (asset == null) {
            throw ApiException.of(ErrorCode.ASSET_NOT_FOUND);
        }
        return asset;
    }

    private TemplateLimits limitsFor(String templateId) {
        if (templateId == null || templateId.isBlank()) {
            return new TemplateLimits(null, DEFAULT_MIME_TYPES, storageProperties.getMaxFileSizeBytes());
        }
        if (templateId.length() > 64 || !TEMPLATE_ID.matcher(templateId).matches()) {
            throw ApiException.validation(List.of(new FieldErrorView("templateId",
                    messages.get("validation.templateId.format"))));
        }
        return templateService.limits(templateId);
    }

    private void deleteFileQuietly(String storageKey) {
        try {
            objectStorage.delete(storageKey);
        } catch (StorageException ex) {
            log.warn("asset.file_delete_failed storageKey={}", storageKey, ex);
        }
    }
}
