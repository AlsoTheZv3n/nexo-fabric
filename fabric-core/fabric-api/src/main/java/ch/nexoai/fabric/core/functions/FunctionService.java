package ch.nexoai.fabric.core.functions;

import ch.nexoai.fabric.adapters.out.persistence.entity.FunctionEntity;
import ch.nexoai.fabric.adapters.out.persistence.entity.FunctionExecutionEntity;
import ch.nexoai.fabric.adapters.out.persistence.entity.OntologyObjectEntity;
import ch.nexoai.fabric.adapters.out.persistence.repository.JpaFunctionExecutionRepository;
import ch.nexoai.fabric.adapters.out.persistence.repository.JpaFunctionRepository;
import ch.nexoai.fabric.adapters.out.persistence.repository.JpaOntologyObjectRepository;
import ch.nexoai.fabric.core.tenant.TenantContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.mozilla.javascript.Context;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class FunctionService {

    private final JpaFunctionRepository functionRepository;
    private final JpaFunctionExecutionRepository executionRepository;
    private final JpaOntologyObjectRepository objectRepository;
    private final FunctionExecutionEngine engine;
    private final ObjectMapper objectMapper;

    // ─── CRUD ─────────────────────────────────────────────────────────────────

    public FunctionDefinition create(CreateFunctionRequest request, UUID tenantId) {
        validateSyntax(request.getSourceCode());

        FunctionEntity entity = FunctionEntity.builder()
                .tenantId(tenantId)
                .apiName(request.getApiName())
                .displayName(request.getDisplayName())
                .description(request.getDescription())
                .language("javascript")
                .sourceCode(request.getSourceCode())
                .inputType(request.getInputType())
                .outputType(request.getOutputType())
                .isActive(true)
                .build();

        return functionRepository.save(entity).toDomain();
    }

    public FunctionDefinition update(String apiName, CreateFunctionRequest request, UUID tenantId) {
        FunctionEntity entity = functionRepository.findByApiNameAndTenantId(apiName, tenantId)
                .orElseThrow(() -> new FunctionNotFoundException(apiName));

        validateSyntax(request.getSourceCode());

        entity.setDisplayName(request.getDisplayName());
        entity.setDescription(request.getDescription());
        entity.setSourceCode(request.getSourceCode());
        entity.setInputType(request.getInputType());
        entity.setOutputType(request.getOutputType());

        return functionRepository.save(entity).toDomain();
    }

    public void delete(String apiName, UUID tenantId) {
        FunctionEntity entity = functionRepository.findByApiNameAndTenantId(apiName, tenantId)
                .orElseThrow(() -> new FunctionNotFoundException(apiName));
        functionRepository.delete(entity);
    }

    @Transactional(readOnly = true)
    public List<FunctionDefinition> findAll(UUID tenantId) {
        return functionRepository.findByTenantId(tenantId).stream()
                .map(FunctionEntity::toDomain).toList();
    }

    @Transactional(readOnly = true)
    public FunctionDefinition findByApiName(String apiName, UUID tenantId) {
        return functionRepository.findByApiNameAndTenantId(apiName, tenantId)
                .orElseThrow(() -> new FunctionNotFoundException(apiName))
                .toDomain();
    }

    // ─── Execution ────────────────────────────────────────────────────────────

    public FunctionExecutionResult executeForObject(String apiName, UUID objectId, UUID tenantId) {
        FunctionDefinition function = findByApiName(apiName, tenantId);
        OntologyObjectEntity object = objectRepository.findById(objectId)
                .orElseThrow(() -> new IllegalArgumentException("Object not found: " + objectId));

        JsonNode inputData = parseProperties(object);
        FunctionExecutionResult result = engine.execute(function, inputData);

        saveExecutionLog(function, objectId, inputData, result, tenantId);

        log.info("[Functions] '{}' on {} -> {} ({}ms)",
                apiName, objectId, result.isSuccess() ? "OK" : "FAIL", result.getDurationMs());

        return result;
    }

    public FunctionExecutionResult execute(String apiName, JsonNode inputData, UUID tenantId) {
        FunctionDefinition function = findByApiName(apiName, tenantId);
        FunctionExecutionResult result = engine.execute(function, inputData);
        saveExecutionLog(function, null, inputData, result, tenantId);
        return result;
    }

    /** Test-run code without persisting it. */
    public FunctionExecutionResult test(String sourceCode, JsonNode testInput) {
        FunctionDefinition tempFn = FunctionDefinition.builder()
                .apiName("__test__")
                .sourceCode(sourceCode)
                .language("javascript")
                .build();
        return engine.execute(tempFn, testInput);
    }

    @Transactional(readOnly = true)
    public List<FunctionExecutionEntity> getExecutions(String apiName, UUID tenantId, int limit) {
        FunctionEntity fn = functionRepository.findByApiNameAndTenantId(apiName, tenantId)
                .orElseThrow(() -> new FunctionNotFoundException(apiName));
        return executionRepository.findByFunctionIdOrderByExecutedAtDesc(fn.getId(), PageRequest.of(0, limit));
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private void validateSyntax(String code) {
        Context cx = Context.enter();
        try {
            cx.setOptimizationLevel(-1);
            cx.compileString("(function(){\n" + code + "\n})", "validation", 1, null);
        } catch (Exception e) {
            throw new FunctionSyntaxException("JavaScript syntax error: " + e.getMessage());
        } finally {
            Context.exit();
        }
    }

    private JsonNode parseProperties(OntologyObjectEntity object) {
        try {
            String props = object.getProperties();
            if (props == null) return objectMapper.createObjectNode();
            return objectMapper.readTree(props);
        } catch (Exception e) {
            return objectMapper.createObjectNode();
        }
    }

    private void saveExecutionLog(FunctionDefinition fn, UUID objectId,
                                   JsonNode input, FunctionExecutionResult result, UUID tenantId) {
        FunctionExecutionEntity entry = FunctionExecutionEntity.builder()
                .tenantId(tenantId)
                .functionId(fn.getId())
                .objectId(objectId)
                .inputData(input != null ? input.toString() : "{}")
                .outputData(result.getOutput() != null ? result.getOutput().toString() : null)
                .errorMessage(result.getError())
                .durationMs((int) result.getDurationMs())
                .status(result.isSuccess() ? "SUCCESS" : "FAILED")
                .executedBy(TenantContext.getCurrentUser())
                .executedAt(Instant.now())
                .build();
        executionRepository.save(entry);
    }
}
