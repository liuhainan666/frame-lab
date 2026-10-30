package com.framelab.template;

import java.time.Duration;

import com.framelab.template.dto.CategoryListView;
import com.framelab.template.dto.TemplateDetailView;
import com.framelab.template.dto.TemplateListView;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class TemplateController {

    private static final CacheControl PUBLIC_CACHE = CacheControl.maxAge(Duration.ofSeconds(60)).cachePublic();

    private final TemplateService templateService;

    public TemplateController(TemplateService templateService) {
        this.templateService = templateService;
    }

    @GetMapping("/api/template-categories")
    public ResponseEntity<CategoryListView> categories() {
        return ResponseEntity.ok().cacheControl(PUBLIC_CACHE).body(templateService.categories());
    }

    @GetMapping("/api/templates")
    public ResponseEntity<TemplateListView> templates(
            @RequestParam(value = "category", required = false) String category) {
        return ResponseEntity.ok().cacheControl(PUBLIC_CACHE).body(templateService.list(category));
    }

    @GetMapping("/api/templates/{id}")
    public ResponseEntity<TemplateDetailView> template(@PathVariable("id") String id) {
        return ResponseEntity.ok().cacheControl(PUBLIC_CACHE).body(templateService.detail(id));
    }
}
