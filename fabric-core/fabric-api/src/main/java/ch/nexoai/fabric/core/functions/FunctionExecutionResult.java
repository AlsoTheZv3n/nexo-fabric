package ch.nexoai.fabric.core.functions;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class FunctionExecutionResult {
    private final boolean success;
    private final JsonNode output;
    private final String error;
    private final long durationMs;

    public static FunctionExecutionResult success(JsonNode output, long durationMs) {
        return FunctionExecutionResult.builder()
                .success(true).output(output).durationMs(durationMs).build();
    }

    public static FunctionExecutionResult failure(String error, long durationMs) {
        return FunctionExecutionResult.builder()
                .success(false).error(error).durationMs(durationMs).build();
    }
}
