package com.framelab.asset;

import java.net.URI;
import java.time.Duration;

import com.framelab.asset.dto.AssetView;
import com.framelab.common.security.SecurityUtils;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
public class AssetController {

    private static final CacheControl PRIVATE_CACHE =
            CacheControl.maxAge(Duration.ofSeconds(300)).cachePrivate();

    private final AssetService assetService;

    public AssetController(AssetService assetService) {
        this.assetService = assetService;
    }

    @PostMapping(value = "/api/assets", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<AssetView> upload(@RequestPart("file") MultipartFile file,
                                            @RequestParam(value = "templateId", required = false) String templateId) {
        AssetView asset = assetService.upload(SecurityUtils.currentUser().id(), file, templateId);
        return ResponseEntity.created(URI.create("/api/assets/" + asset.id() + "/content")).body(asset);
    }

    @GetMapping("/api/assets/{id}/content")
    public ResponseEntity<Resource> content(@PathVariable("id") String id,
                                            @RequestHeader(value = HttpHeaders.IF_NONE_MATCH, required = false)
                                            String ifNoneMatch) {
        AssetContent content = assetService.content(SecurityUtils.currentUser().id(), id);
        UploadAsset asset = content.asset();
        String etag = "W/\"" + asset.getId() + "\"";
        if (matchesEtag(ifNoneMatch, asset.getId())) {
            return ResponseEntity.status(HttpStatus.NOT_MODIFIED)
                    .header(HttpHeaders.ETAG, etag)
                    .cacheControl(PRIVATE_CACHE)
                    .build();
        }
        String extension = "image/png".equals(asset.getMimeType()) ? "png" : "jpg";
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(asset.getMimeType()))
                .contentLength(asset.getSizeBytes())
                .cacheControl(PRIVATE_CACHE)
                .header(HttpHeaders.ETAG, etag)
                .header("X-Content-Type-Options", "nosniff")
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "inline; filename=\"asset-" + asset.getId() + "." + extension + "\"")
                .body(content.resource());
    }

    @DeleteMapping("/api/assets/{id}")
    public ResponseEntity<Void> delete(@PathVariable("id") String id) {
        assetService.delete(SecurityUtils.currentUser().id(), id);
        return ResponseEntity.noContent().build();
    }

    private boolean matchesEtag(String header, String assetId) {
        if (header == null || header.isBlank()) {
            return false;
        }
        String normalized = header.replace("W/", "").trim();
        return "*".equals(normalized) || normalized.contains("\"" + assetId + "\"");
    }
}
