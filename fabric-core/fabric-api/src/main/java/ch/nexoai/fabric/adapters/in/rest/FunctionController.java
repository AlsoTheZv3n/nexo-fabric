package ch.nexoai.fabric.adapters.in.rest;

import ch.nexoai.fabric.adapters.out.persistence.entity.FunctionExecutionEntity;
import ch.nexoai.fabric.core.functions.CreateFunctionRequest;
import ch.nexoai.fabric.core.functions.FunctionDefinition;
import ch.nexoai.fabric.core.functions.FunctionExecutionResult;
import ch.nexoai.fabric.core.functions.FunctionService;
import ch.nexoai.fabric.core.tenant.TenantContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/functions")
@RequiredArgsConstructor
public class FunctionController {

    private final FunctionService functionService;
    private final ObjectMapper objectMapper;

    @GetMapping
    public List<FunctionDefinition> list() {
        return functionService.findAll(TenantContext.getTenantId());
    }

    @GetMapping("/{apiName}")
    public FunctionDefinition get(@PathVariable String apiName) {
        return functionService.findByApiName(apiName, TenantContext.getTenantId());
    }

    @PostMapping
    public ResponseEntity<FunctionDefinition> create(@RequestBody CreateFunctionRequest request) {
        FunctionDefinition fn = functionService.create(request, TenantContext.getTenantId());
        return ResponseEntity.status(201).body(fn);
    }

    @PutMapping("/{apiName}")
    public FunctionDefinition update(@PathVariable String apiName,
                                      @RequestBody CreateFunctionRequest request) {
        return functionService.update(apiName, request, TenantContext.getTenantId());
    }

    @DeleteMapping("/{apiName}")
    public ResponseEntity<Void> delete(@PathVariable String apiName) {
        functionService.delete(apiName, TenantContext.getTenantId());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{apiName}/execute")
    public FunctionExecutionResult execute(@PathVariable String apiName,
                                            @RequestBody(required = false) JsonNode input) {
        JsonNode inputData = input != null ? input : objectMapper.createObjectNode();
        return functionService.execute(apiName, inputData, TenantContext.getTenantId());
    }

    @PostMapping("/{apiName}/execute/object/{objectId}")
    public FunctionExecutionResult executeForObject(@PathVariable String apiName,
                                                     @PathVariable UUID objectId) {
        return functionService.executeForObject(apiName, objectId, TenantContext.getTenantId());
    }

    /** Test endpoint: runs code without persisting it. */
    @PostMapping("/test")
    public FunctionExecutionResult test(@RequestBody Map<String, Object> body) {
        String code = (String) body.get("code");
        Object testInput = body.get("input");
        JsonNode input = testInput != null
                ? objectMapper.valueToTree(testInput)
                : objectMapper.createObjectNode();
        return functionService.test(code, input);
    }

    @GetMapping("/{apiName}/executions")
    public List<Map<String, Object>> getExecutions(@PathVariable String apiName,
                                                    @RequestParam(defaultValue = "20") int limit) {
        List<FunctionExecutionEntity> executions = functionService.getExecutions(
                apiName, TenantContext.getTenantId(), limit);
        return executions.stream().map(e -> {
            Map<String, Object> m = new java.util.LinkedHashMap<>();
            m.put("id", e.getId().toString());
            m.put("objectId", e.getObjectId() != null ? e.getObjectId().toString() : null);
            m.put("inputData", e.getInputData());
            m.put("outputData", e.getOutputData());
            m.put("errorMessage", e.getErrorMessage());
            m.put("durationMs", e.getDurationMs());
            m.put("status", e.getStatus());
            m.put("executedBy", e.getExecutedBy());
            m.put("executedAt", e.getExecutedAt());
            return m;
        }).toList();
    }
}
