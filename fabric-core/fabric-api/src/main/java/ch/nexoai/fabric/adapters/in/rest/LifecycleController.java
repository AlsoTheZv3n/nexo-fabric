package ch.nexoai.fabric.adapters.in.rest;

import ch.nexoai.fabric.core.lifecycle.LifecycleDefinition;
import ch.nexoai.fabric.core.lifecycle.LifecycleService;
import ch.nexoai.fabric.core.lifecycle.TransitionResult;
import ch.nexoai.fabric.core.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/lifecycle")
@RequiredArgsConstructor
public class LifecycleController {

    private final LifecycleService lifecycleService;

    @GetMapping("/{objectTypeApiName}")
    public ResponseEntity<LifecycleDefinition> getDefinition(@PathVariable String objectTypeApiName) {
        return lifecycleService.findForObjectType(objectTypeApiName, TenantContext.getTenantId())
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/{objectTypeApiName}")
    public LifecycleDefinition upsertDefinition(@PathVariable String objectTypeApiName,
                                                 @RequestBody LifecycleDefinition definition) {
        return lifecycleService.upsert(objectTypeApiName, definition, TenantContext.getTenantId());
    }

    @DeleteMapping("/{objectTypeApiName}")
    public ResponseEntity<Void> delete(@PathVariable String objectTypeApiName) {
        lifecycleService.delete(objectTypeApiName, TenantContext.getTenantId());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/objects/{objectId}/transition")
    public TransitionResult transition(@PathVariable UUID objectId,
                                        @RequestBody Map<String, String> body) {
        String toState = body.get("toState");
        return lifecycleService.transition(objectId, toState,
                TenantContext.getCurrentUser(), TenantContext.getTenantId());
    }

    @GetMapping("/objects/{objectId}/available-transitions")
    public List<LifecycleDefinition.Transition> availableTransitions(@PathVariable UUID objectId) {
        return lifecycleService.getAvailableTransitions(objectId, TenantContext.getTenantId());
    }
}
