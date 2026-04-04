package ch.nexoai.fabric.engine.port.out;

public interface EmbeddingPort {
    float[] embed(String text);
}
