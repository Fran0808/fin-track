package com.store.api.controller.category;

import com.store.api.model.dto.category.ClassificationRuleRequest;
import com.store.api.model.dto.category.ClassificationRuleResponse;
import com.store.api.service.category.ClassificationRuleService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/classification-rules")
@RequiredArgsConstructor
public class ClassificationRuleController {
    private final ClassificationRuleService service;

    @GetMapping
    public List<ClassificationRuleResponse> list() { return service.list(); }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ClassificationRuleResponse create(@Valid @RequestBody ClassificationRuleRequest request) {
        return service.create(request);
    }

    @PutMapping("/{id}")
    public ClassificationRuleResponse update(@PathVariable Long id, @Valid @RequestBody ClassificationRuleRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) { service.delete(id); }
}
