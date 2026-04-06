package ch.nexoai.fabric.core.lifecycle;

import java.util.UUID;

public record TransitionResult(
        UUID objectId,
        String fromState,
        String toState,
        boolean success
) {}
