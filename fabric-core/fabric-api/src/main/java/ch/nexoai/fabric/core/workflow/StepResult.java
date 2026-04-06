package ch.nexoai.fabric.core.workflow;

import lombok.Builder;
import lombok.Getter;

import java.time.Instant;

@Getter
@Builder
public class StepResult {
    private final String nextStepId;
    private final boolean waiting;
    private final boolean failed;
    private final String error;
    private final Instant resumeAt;
    private final Object output;

    public static StepResult next(String nextStepId) {
        return StepResult.builder().nextStepId(nextStepId).build();
    }

    public static StepResult waiting(Instant resumeAt, String nextStepId) {
        return StepResult.builder().waiting(true).resumeAt(resumeAt).nextStepId(nextStepId).build();
    }

    public static StepResult failed(String error) {
        return StepResult.builder().failed(true).error(error).build();
    }
}
