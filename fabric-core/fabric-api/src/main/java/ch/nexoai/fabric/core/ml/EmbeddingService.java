package ch.nexoai.fabric.core.ml;

import ai.djl.MalformedModelException;
import ai.djl.inference.Predictor;
import ai.djl.repository.zoo.Criteria;
import ai.djl.repository.zoo.ModelNotFoundException;
import ai.djl.repository.zoo.ZooModel;
import ai.djl.training.util.ProgressBar;
import ai.djl.translate.TranslateException;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Generates dense vector embeddings for semantic search.
 *
 * Primary:  ONNX export of sentence-transformers/all-MiniLM-L6-v2 via DJL.
 *           Downloaded on first start (~23 MB), cached in modelCacheDir.
 *           Requires glibc (Ubuntu/Debian base image) — not Alpine/musl.
 *
 * Fallback: Deterministic hash-based pseudo-embedding when ONNX is unavailable.
 *           Semantic search still works but similarity scores are not meaningful.
 */
@Service
@Slf4j
public class EmbeddingService {

    private static final String MODEL_NAME = "sentence-transformers/all-MiniLM-L6-v2";
    private static final String MODEL_ONNX_URL =
        "https://huggingface.co/optimum/all-MiniLM-L6-v2/resolve/main/model.onnx";
    private static final String TOKENIZER_URL =
        "https://huggingface.co/sentence-transformers/all-MiniLM-L6-v2/resolve/main/tokenizer.json";

    public static final int EMBEDDING_DIMENSION = 384;

    @Value("${nexo.ml.model-cache-dir:${java.io.tmpdir}/nexo-models}")
    private String modelCacheDir;

    private ZooModel<String, float[]>  model;
    private Predictor<String, float[]> predictor;
    private boolean                    modelAvailable = false;

    // ─── Lifecycle ────────────────────────────────────────────────────────────

    @PostConstruct
    public void init() {
        System.setProperty("DJL_CACHE_DIR", modelCacheDir);

        try {
            log.info("Loading ONNX embedding model: {} (cache: {})", MODEL_NAME, modelCacheDir);

            // Download model.onnx and tokenizer.json from HuggingFace if not cached
            Path modelDir = Path.of(modelCacheDir, "all-MiniLM-L6-v2");
            Files.createDirectories(modelDir);
            downloadIfMissing(modelDir.resolve("model.onnx"), MODEL_ONNX_URL, "ONNX model");
            downloadIfMissing(modelDir.resolve("tokenizer.json"), TOKENIZER_URL, "tokenizer");

            Criteria<String, float[]> criteria = Criteria.builder()
                .setTypes(String.class, float[].class)
                .optModelPath(modelDir)
                .optEngine("OnnxRuntime")
                .optTranslator(new SentenceTransformerTranslator(modelDir))
                .optProgress(new ProgressBar())
                .build();

            model     = criteria.loadModel();
            predictor = model.newPredictor();

            // Smoke-test: verify output dimension
            float[] probe = predictor.predict("test");
            if (probe.length != EMBEDDING_DIMENSION) {
                throw new IllegalStateException(
                    "Unexpected embedding dimension: " + probe.length
                    + " (expected " + EMBEDDING_DIMENSION + ")");
            }

            modelAvailable = true;
            log.info("ONNX embedding model ready — dimension: {}, real inference active",
                EMBEDDING_DIMENSION);

        } catch (ModelNotFoundException e) {
            log.warn("ONNX model not found ({}). Check internet access on first run. " +
                "Using hash-based fallback.", e.getMessage());
        } catch (MalformedModelException e) {
            log.warn("ONNX model malformed: {}. Using hash-based fallback.", e.getMessage());
        } catch (IOException e) {
            log.warn("I/O error loading ONNX model: {}. Using hash-based fallback.", e.getMessage());
        } catch (TranslateException e) {
            log.warn("ONNX smoke-test failed: {}. Using hash-based fallback.", e.getMessage());
            closeQuietly();
        } catch (UnsatisfiedLinkError e) {
            log.warn("Native ONNX library missing (Alpine/musl not supported — use Ubuntu base " +
                "image). Using hash-based fallback. Error: {}", e.getMessage());
            closeQuietly();
        } catch (Exception e) {
            log.warn("Unexpected error loading ONNX model: {}. Using hash-based fallback.",
                e.getMessage());
            closeQuietly();
        }
    }

    @PreDestroy
    public void close() {
        closeQuietly();
    }

    // ─── Public API ──────────────────────────────────────────────────────────

    public boolean isAvailable()  { return modelAvailable; }
    public String  getModelName() { return MODEL_NAME; }

    public float[] embed(String text) {
        if (text == null || text.isBlank()) {
            return new float[EMBEDDING_DIMENSION];
        }

        String normalized = normalize(text);

        if (modelAvailable && predictor != null) {
            try {
                return predictor.predict(normalized);
            } catch (TranslateException e) {
                log.warn("ONNX inference failed, using hash fallback: {}", e.getMessage());
            }
        }

        return hashEmbedding(normalized);
    }

    public float[] embedObject(JsonNode properties) {
        return embed(buildObjectText(properties));
    }

    // ─── Private helpers ─────────────────────────────────────────────────────

    private String buildObjectText(JsonNode properties) {
        if (properties == null) return "";
        StringBuilder sb = new StringBuilder();
        properties.fields().forEachRemaining(entry -> {
            JsonNode v = entry.getValue();
            if (v.isTextual() || v.isNumber()) {
                sb.append(entry.getKey()).append(": ").append(v.asText()).append(". ");
            }
        });
        return sb.toString().strip();
    }

    private String normalize(String text) {
        return text.replaceAll("\\s+", " ")
                   .substring(0, Math.min(text.length(), 2000))
                   .strip();
    }

    private float[] hashEmbedding(String text) {
        float[] embedding = new float[EMBEDDING_DIMENSION];
        String[] words = text.toLowerCase().split("\\s+");
        for (int w = 0; w < words.length; w++) {
            int hash = words[w].hashCode();
            for (int i = 0; i < 8; i++) {
                int idx = Math.abs((hash + i * 31 + w * 7) % EMBEDDING_DIMENSION);
                embedding[idx] += 1.0f / (words.length > 0 ? words.length : 1);
            }
        }
        return l2normalize(embedding);
    }

    private float[] l2normalize(float[] v) {
        float norm = 0;
        for (float x : v) norm += x * x;
        norm = (float) Math.sqrt(norm);
        if (norm > 0) for (int i = 0; i < v.length; i++) v[i] /= norm;
        return v;
    }

    private void downloadIfMissing(Path target, String url, String label) throws IOException {
        if (Files.exists(target)) {
            log.info("Using cached {}: {}", label, target);
            return;
        }
        log.info("Downloading {} from {}...", label, url);
        try (InputStream in = URI.create(url).toURL().openStream()) {
            Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
        }
        log.info("{} downloaded: {} ({} KB)", label, target, Files.size(target) / 1024);
    }

    private void closeQuietly() {
        modelAvailable = false;
        if (predictor != null) { try { predictor.close(); } catch (Exception ignored) {} predictor = null; }
        if (model     != null) { try { model.close();     } catch (Exception ignored) {} model     = null; }
    }
}
