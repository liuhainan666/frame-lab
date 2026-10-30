package com.framelab.task;

import java.net.URI;
import java.time.Duration;

import com.framelab.common.security.SecurityUtils;
import com.framelab.task.dto.TaskDetailView;
import com.framelab.task.dto.TaskPageView;
import com.framelab.task.dto.TaskSubmitRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/tasks")
public class TaskController {

    private static final CacheControl RESULT_CACHE =
            CacheControl.maxAge(Duration.ofSeconds(86400)).cachePrivate();

    private final TaskSubmissionService taskSubmissionService;
    private final TaskQueryService taskQueryService;
    private final TaskResultService taskResultService;

    public TaskController(TaskSubmissionService taskSubmissionService,
                          TaskQueryService taskQueryService,
                          TaskResultService taskResultService) {
        this.taskSubmissionService = taskSubmissionService;
        this.taskQueryService = taskQueryService;
        this.taskResultService = taskResultService;
    }

    @PostMapping
    public ResponseEntity<TaskDetailView> submit(@RequestBody(required = false) TaskSubmitRequest request,
                                                 HttpServletResponse response) {
        noStoreVaryLanguage(response);
        TaskSubmissionOutcome outcome = taskSubmissionService.submit(SecurityUtils.currentUser().id(), request);
        ResponseEntity.BodyBuilder builder = ResponseEntity
                .status(outcome.replay() ? HttpStatus.OK : HttpStatus.ACCEPTED)
                .header(HttpHeaders.VARY, HttpHeaders.ACCEPT_LANGUAGE);
        if (outcome.replay()) {
            builder.header("Idempotent-Replay", "true");
        } else {
            builder.location(URI.create("/api/tasks/" + outcome.task().id()));
        }
        return builder.body(outcome.task());
    }

    @GetMapping
    public TaskPageView list(@RequestParam(value = "status", defaultValue = "all") String status,
                             @RequestParam(value = "templateId", required = false) String templateId,
                             @RequestParam(value = "page", defaultValue = "1") int page,
                             @RequestParam(value = "size", defaultValue = "20") int size,
                             HttpServletResponse response) {
        noStoreVaryLanguage(response);
        return taskQueryService.list(SecurityUtils.currentUser().id(), status, templateId, page, size);
    }

    @GetMapping("/{id}")
    public TaskDetailView detail(@PathVariable("id") String id, HttpServletResponse response) {
        noStoreVaryLanguage(response);
        return taskQueryService.detail(SecurityUtils.currentUser().id(), id);
    }

    @GetMapping("/{id}/result")
    public ResponseEntity<Resource> result(@PathVariable("id") String id,
                                           @RequestHeader(value = HttpHeaders.IF_NONE_MATCH, required = false)
                                           String ifNoneMatch) {
        TaskResultContent content = taskResultService.content(SecurityUtils.currentUser().id(), id);
        TaskRow task = content.task();
        String etag = "W/\"" + task.getId() + "-result\"";
        if (matchesEtag(ifNoneMatch, task.getId())) {
            return ResponseEntity.status(HttpStatus.NOT_MODIFIED)
                    .cacheControl(RESULT_CACHE)
                    .header(HttpHeaders.ETAG, etag)
                    .header("X-Content-Type-Options", "nosniff")
                    .build();
        }
        String shortId = task.getId().substring(0, Math.min(8, task.getId().length()));
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_JPEG)
                .contentLength(task.getResultSizeBytes() == null ? 0 : task.getResultSizeBytes())
                .cacheControl(RESULT_CACHE)
                .header(HttpHeaders.ETAG, etag)
                .header("X-Content-Type-Options", "nosniff")
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "inline; filename=\"framelab-" + shortId + ".jpg\"")
                .body(content.resource());
    }

    private void noStoreVaryLanguage(HttpServletResponse response) {
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        com.framelab.common.web.ResponseHeaders.addVary(response, HttpHeaders.ACCEPT_LANGUAGE);
    }

    private boolean matchesEtag(String header, String taskId) {
        if (header == null || header.isBlank()) {
            return false;
        }
        String normalized = header.replace("W/", "").trim();
        return "*".equals(normalized) || normalized.contains("\"" + taskId + "-result\"");
    }
}
