package ch.nexoai.fabric.core.workflow;

import ch.nexoai.fabric.core.functions.FunctionExecutionResult;
import ch.nexoai.fabric.core.functions.FunctionService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.ScriptableObject;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Multi-step workflow executor with WAIT/CONDITION/CALL_FUNCTION support
 * and resumable runs persisted in workflow_runs.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WorkflowExecutor {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final FunctionService functionService;

    // ─── Start ───────────────────────────────────────────────────────────────

    @Async
    public void start(UUID workflowId, JsonNode triggerData, UUID tenantId) {
        Map<String, Object> workflow;
        try {
            workflow = jdbcTemplate.queryForMap(
                    "SELECT * FROM workflows WHERE id = ?::uuid AND is_active = TRUE",
                    workflowId.toString());
        } catch (Exception e) {
            log.warn("[Workflow] Workflow not found or inactive: {}", workflowId);
            return;
        }

        List<WorkflowStep> steps = parseSteps(workflow.get("steps"));
        if (steps.isEmpty()) {
            log.warn("[Workflow] No steps in workflow {}", workflowId);
            return;
        }

        UUID runId = UUID.randomUUID();
        String firstStepId = steps.get(0).getId();
        jdbcTemplate.update(
                "INSERT INTO workflow_runs (id, workflow_id, tenant_id, status, trigger_data, current_step, context, started_at) " +
                "VALUES (?::uuid, ?::uuid, ?::uuid, 'RUNNING', ?::jsonb, ?, ?::jsonb, NOW())",
                runId.toString(), workflowId.toString(), tenantId.toString(),
                triggerData != null ? triggerData.toString() : "{}",
                firstStepId,
                triggerData != null ? triggerData.toString() : "{}");

        executeFrom(runId, workflowId, tenantId, steps, firstStepId, triggerData);
    }

    // ─── Resume (for WAIT steps) ─────────────────────────────────────────────

    @Scheduled(fixedDelay = 10_000)
    public void resumeWaitingRuns() {
        List<Map<String, Object>> ready;
        try {
            ready = jdbcTemplate.queryForList(
                    "SELECT * FROM workflow_runs WHERE status = 'WAITING' AND resume_at <= NOW() LIMIT 50");
        } catch (Exception e) {
            return; // table missing or DB unavailable
        }

        for (Map<String, Object> run : ready) {
            UUID runId = UUID.fromString(run.get("id").toString());
            UUID workflowId = UUID.fromString(run.get("workflow_id").toString());
            UUID tenantId = UUID.fromString(run.get("tenant_id").toString());
            String currentStep = (String) run.get("current_step");

            log.info("[Workflow] Resuming run {}", runId);
            jdbcTemplate.update(
                    "UPDATE workflow_runs SET status = 'RUNNING', resume_at = NULL WHERE id = ?::uuid",
                    runId.toString());

            try {
                Map<String, Object> workflow = jdbcTemplate.queryForMap(
                        "SELECT * FROM workflows WHERE id = ?::uuid", workflowId.toString());
                List<WorkflowStep> steps = parseSteps(workflow.get("steps"));
                JsonNode context = parseJson(run.get("context"));
                executeFrom(runId, workflowId, tenantId, steps, currentStep, context);
            } catch (Exception e) {
                markFailed(runId, "Resume failed: " + e.getMessage());
            }
        }
    }

    // ─── Execution Loop ──────────────────────────────────────────────────────

    private void executeFrom(UUID runId, UUID workflowId, UUID tenantId,
                              List<WorkflowStep> steps, String startStepId, JsonNode context) {
        Map<String, WorkflowStep> stepMap = new HashMap<>();
        steps.forEach(s -> stepMap.put(s.getId(), s));

        String currentStepId = startStepId;
        int safetyCounter = 0;

        while (currentStepId != null && !"end".equalsIgnoreCase(currentStepId)) {
            if (++safetyCounter > 100) {
                markFailed(runId, "Step limit exceeded (>100) — possible infinite loop");
                return;
            }

            WorkflowStep step = stepMap.get(currentStepId);
            if (step == null) {
                markFailed(runId, "Step not found: " + currentStepId);
                return;
            }

            log.info("[Workflow] Run {} -> step '{}' ({})", runId, step.getName(), step.getType());
            jdbcTemplate.update(
                    "UPDATE workflow_runs SET current_step = ? WHERE id = ?::uuid",
                    currentStepId, runId.toString());

            StepResult result;
            try {
                result = executeStep(step, context, runId, tenantId);
            } catch (Exception e) {
                log.error("[Workflow] Step '{}' threw exception: {}", step.getName(), e.getMessage());
                markFailed(runId, e.getMessage());
                return;
            }

            appendStepLog(runId, step, result);

            if (result.isWaiting()) {
                jdbcTemplate.update(
                        "UPDATE workflow_runs SET status = 'WAITING', resume_at = ?, current_step = ? WHERE id = ?::uuid",
                        OffsetDateTime.ofInstant(result.getResumeAt(), java.time.ZoneOffset.UTC), result.getNextStepId(), runId.toString());
                return;
            }

            if (result.isFailed()) {
                markFailed(runId, result.getError());
                return;
            }

            currentStepId = result.getNextStepId();
        }

        // Workflow complete
        jdbcTemplate.update(
                "UPDATE workflow_runs SET status = 'SUCCESS', finished_at = NOW(), current_step = NULL WHERE id = ?::uuid",
                runId.toString());
        log.info("[Workflow] Run {} completed", runId);
    }

    // ─── Step Execution ──────────────────────────────────────────────────────

    private StepResult executeStep(WorkflowStep step, JsonNode context, UUID runId, UUID tenantId) {
        Map<String, Object> config = step.getConfig() != null ? step.getConfig() : Map.of();

        return switch (step.getType()) {

            case WAIT -> {
                int amount = ((Number) config.getOrDefault("amount", 1)).intValue();
                String unit = (String) config.getOrDefault("unit", "SECONDS");
                ChronoUnit chronoUnit;
                try {
                    chronoUnit = ChronoUnit.valueOf(unit);
                } catch (Exception e) {
                    chronoUnit = ChronoUnit.SECONDS;
                }
                Instant resumeAt = Instant.now().plus(amount, chronoUnit);
                yield StepResult.waiting(resumeAt, step.getNext());
            }

            case CONDITION -> {
                String expression = (String) config.getOrDefault("expression", "true");
                boolean result = evaluateCondition(expression, context);
                yield StepResult.next(result ? step.getTruePath() : step.getFalsePath());
            }

            case CALL_FUNCTION -> {
                String fnName = (String) config.get("functionApiName");
                if (fnName == null) yield StepResult.failed("CALL_FUNCTION requires functionApiName");
                FunctionExecutionResult fr = functionService.execute(fnName, context, tenantId);
                if (!fr.isSuccess()) yield StepResult.failed("Function failed: " + fr.getError());
                yield StepResult.next(step.getNext());
            }

            case NOTIFY -> {
                String title = (String) config.getOrDefault("title", "Workflow notification");
                log.info("[Workflow] NOTIFY: {} (run {})", title, runId);
                yield StepResult.next(step.getNext());
            }

            case EXECUTE_ACTION -> {
                String actionType = (String) config.get("actionType");
                log.info("[Workflow] EXECUTE_ACTION: {} (run {})", actionType, runId);
                yield StepResult.next(step.getNext());
            }

            case END -> StepResult.next(null);
        };
    }

    private boolean evaluateCondition(String expression, JsonNode context) {
        Context cx = Context.enter();
        try {
            cx.setOptimizationLevel(-1);
            Scriptable scope = cx.initStandardObjects();
            String json = context != null ? context.toString() : "{}";
            Object jsCtx = cx.evaluateString(scope, "(" + json + ")", "ctx", 1, null);
            ScriptableObject.putProperty(scope, "object", jsCtx);
            Object result = cx.evaluateString(scope, expression, "condition", 1, null);
            return Context.toBoolean(result);
        } catch (Exception e) {
            log.warn("[Workflow] Condition eval failed: {}", e.getMessage());
            return false;
        } finally {
            Context.exit();
        }
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private void markFailed(UUID runId, String error) {
        jdbcTemplate.update(
                "UPDATE workflow_runs SET status = 'FAILED', finished_at = NOW(), error_message = ? WHERE id = ?::uuid",
                error, runId.toString());
        log.error("[Workflow] Run {} failed: {}", runId, error);
    }

    private List<WorkflowStep> parseSteps(Object stepsObj) {
        try {
            String json = stepsObj.toString();
            return objectMapper.readValue(json, new TypeReference<List<WorkflowStep>>() {});
        } catch (Exception e) {
            log.warn("[Workflow] Failed to parse steps: {}", e.getMessage());
            return List.of();
        }
    }

    private JsonNode parseJson(Object obj) {
        try {
            return obj != null ? objectMapper.readTree(obj.toString()) : objectMapper.createObjectNode();
        } catch (Exception e) {
            return objectMapper.createObjectNode();
        }
    }

    private void appendStepLog(UUID runId, WorkflowStep step, StepResult result) {
        try {
            Map<String, Object> existing = jdbcTemplate.queryForMap(
                    "SELECT step_results FROM workflow_runs WHERE id = ?::uuid", runId.toString());
            String currentLog = existing.get("step_results") != null ? existing.get("step_results").toString() : "[]";
            var arr = (com.fasterxml.jackson.databind.node.ArrayNode) objectMapper.readTree(currentLog);
            var entry = objectMapper.createObjectNode();
            entry.put("stepId", step.getId());
            entry.put("stepName", step.getName());
            entry.put("status", result.isFailed() ? "FAILED" : (result.isWaiting() ? "WAITING" : "SUCCESS"));
            entry.put("timestamp", Instant.now().toString());
            if (result.getError() != null) entry.put("error", result.getError());
            arr.add(entry);
            jdbcTemplate.update(
                    "UPDATE workflow_runs SET step_results = ?::jsonb WHERE id = ?::uuid",
                    arr.toString(), runId.toString());
        } catch (Exception e) {
            log.debug("[Workflow] Failed to append step log: {}", e.getMessage());
        }
    }
}
