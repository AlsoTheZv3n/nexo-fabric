package ch.nexoai.fabric.engine.model;

public record SearchResult(
        FabricObject object,
        float similarity
) {}
