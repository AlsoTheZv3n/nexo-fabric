package ch.nexoai.fabric.core.ml;

import ai.djl.huggingface.tokenizers.Encoding;
import ai.djl.huggingface.tokenizers.HuggingFaceTokenizer;
import ai.djl.ndarray.NDArray;
import ai.djl.ndarray.NDList;
import ai.djl.ndarray.NDManager;
import ai.djl.ndarray.types.DataType;
import ai.djl.translate.NoBatchifyTranslator;
import ai.djl.translate.TranslatorContext;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;

/**
 * Translates a String into a 384-dimensional normalized embedding using
 * the ONNX export of sentence-transformers/all-MiniLM-L6-v2.
 *
 * Pipeline:
 *   1. HuggingFace tokenizer  → input_ids, attention_mask, token_type_ids
 *   2. ONNX model inference   → token-level hidden states [1, seqLen, 384]
 *   3. Attention-masked mean pooling → [1, 384]
 *   4. L2 normalization       → unit vector [384]
 */
@Slf4j
public class SentenceTransformerTranslator implements NoBatchifyTranslator<String, float[]> {

    private static final int MAX_LEN = 512;

    private final Path modelDir;
    private HuggingFaceTokenizer tokenizer;
    private long[]               lastAttentionMask;

    public SentenceTransformerTranslator(Path modelDir) {
        this.modelDir = modelDir;
    }

    @Override
    public void prepare(TranslatorContext ctx) throws IOException {
        // Load tokenizer from local tokenizer.json (downloaded by EmbeddingService)
        tokenizer = HuggingFaceTokenizer.newInstance(modelDir, Map.of(
            "padding",    "true",
            "truncation", "true",
            "maxLength",  String.valueOf(MAX_LEN)
        ));
        log.debug("SentenceTransformerTranslator: tokenizer ready (local)");
    }

    @Override
    public NDList processInput(TranslatorContext ctx, String input) {
        Encoding enc           = tokenizer.encode(input);
        NDManager mgr          = ctx.getNDManager();
        long[]    inputIds     = enc.getIds();
        long[]    attMask      = enc.getAttentionMask();
        long[]    tokenTypeIds = enc.getTypeIds();

        lastAttentionMask = attMask;

        return new NDList(
            mgr.create(inputIds)     .reshape(1, inputIds.length),
            mgr.create(attMask)      .reshape(1, attMask.length),
            mgr.create(tokenTypeIds) .reshape(1, tokenTypeIds.length)
        );
    }

    @Override
    public float[] processOutput(TranslatorContext ctx, NDList list) {
        NDManager mgr = ctx.getNDManager();

        // Model output: last_hidden_state [1, seqLen, 384]
        NDArray tokenEmb = list.get(0);

        // Attention-masked mean pooling
        NDArray mask    = mgr.create(lastAttentionMask)
                             .reshape(1, lastAttentionMask.length, 1)
                             .toType(DataType.FLOAT32, false);
        NDArray sumEmb  = tokenEmb.mul(mask).sum(new int[]{1});
        NDArray sumMask = mask.sum(new int[]{1}).clip(1e-9f, Float.MAX_VALUE);
        NDArray pooled  = sumEmb.div(sumMask);       // [1, 384]

        // L2 normalize → unit vector
        NDArray norm   = pooled.norm(new int[]{1}, true);
        NDArray normed = pooled.div(norm.add(1e-9f));

        return normed.squeeze().toFloatArray();       // [384]
    }
}
