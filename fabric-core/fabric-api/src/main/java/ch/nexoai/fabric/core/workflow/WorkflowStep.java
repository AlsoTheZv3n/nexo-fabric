package ch.nexoai.fabric.core.workflow;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class WorkflowStep {

    public enum Type {
        EXECUTE_ACTION,
        CONDITION,
        WAIT,
        NOTIFY,
        CALL_FUNCTION,
        END
    }

    private String id;
    private Type type;
    private String name;
    private Map<String, Object> config;
    private String next;
    private String truePath;
    private String falsePath;
}
