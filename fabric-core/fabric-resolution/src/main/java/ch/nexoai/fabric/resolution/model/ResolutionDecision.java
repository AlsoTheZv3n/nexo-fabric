package ch.nexoai.fabric.resolution.model;

import java.util.UUID;

public record ResolutionDecision(
        String matchType,
        float confidence,
        String evidence,
        UUID objectA,
        UUID objectB
) {}
