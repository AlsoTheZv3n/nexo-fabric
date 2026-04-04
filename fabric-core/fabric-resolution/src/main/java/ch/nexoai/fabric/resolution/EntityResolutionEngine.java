package ch.nexoai.fabric.resolution;

import ch.nexoai.fabric.engine.model.FabricObject;
import ch.nexoai.fabric.engine.port.out.EmbeddingPort;
import ch.nexoai.fabric.resolution.model.ResolutionDecision;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

public class EntityResolutionEngine {

    private static final Logger log = LoggerFactory.getLogger(EntityResolutionEngine.class);

    private final EmbeddingPort embeddingPort;

    public EntityResolutionEngine(EmbeddingPort embeddingPort) {
        this.embeddingPort = embeddingPort;
    }

    /**
     * Resolve whether two FabricObjects represent the same entity.
     * Applies strategies in order: EXACT_ID, EXACT_EMAIL, FUZZY_NAME, SEMANTIC.
     *
     * @param a first object
     * @param b second object
     * @return resolution decision if a match is found, empty otherwise
     */
    public Optional<ResolutionDecision> resolve(FabricObject a, FabricObject b) {
        log.debug("Resolving entities: {} vs {}", a.id(), b.id());

        // Strategy 1: EXACT_ID - same externalId
        Optional<ResolutionDecision> exactId = tryExactIdMatch(a, b);
        if (exactId.isPresent()) return exactId;

        // Strategy 2: EXACT_EMAIL - email property match
        Optional<ResolutionDecision> exactEmail = tryExactEmailMatch(a, b);
        if (exactEmail.isPresent()) return exactEmail;

        // Strategy 3: FUZZY_NAME - Levenshtein distance < 3
        Optional<ResolutionDecision> fuzzyName = tryFuzzyNameMatch(a, b);
        if (fuzzyName.isPresent()) return fuzzyName;

        // Strategy 4: SEMANTIC - embedding similarity > 0.95
        Optional<ResolutionDecision> semantic = trySemanticMatch(a, b);
        if (semantic.isPresent()) return semantic;

        log.debug("No match found between {} and {}", a.id(), b.id());
        return Optional.empty();
    }

    private Optional<ResolutionDecision> tryExactIdMatch(FabricObject a, FabricObject b) {
        if (a.externalId() != null && a.externalId().equals(b.externalId())) {
            log.debug("EXACT_ID match: externalId={}", a.externalId());
            return Optional.of(new ResolutionDecision(
                    "EXACT_ID", 1.0f,
                    "Matching externalId: " + a.externalId(),
                    a.id(), b.id()
            ));
        }
        return Optional.empty();
    }

    private Optional<ResolutionDecision> tryExactEmailMatch(FabricObject a, FabricObject b) {
        String emailA = extractProperty(a, "email");
        String emailB = extractProperty(b, "email");

        if (emailA != null && emailA.equalsIgnoreCase(emailB)) {
            log.debug("EXACT_EMAIL match: email={}", emailA);
            return Optional.of(new ResolutionDecision(
                    "EXACT_EMAIL", 0.99f,
                    "Matching email: " + emailA,
                    a.id(), b.id()
            ));
        }
        return Optional.empty();
    }

    private Optional<ResolutionDecision> tryFuzzyNameMatch(FabricObject a, FabricObject b) {
        String nameA = extractProperty(a, "name");
        String nameB = extractProperty(b, "name");

        if (nameA == null || nameB == null) {
            return Optional.empty();
        }

        int distance = levenshteinDistance(nameA.toLowerCase(), nameB.toLowerCase());
        if (distance < 3) {
            log.debug("FUZZY_NAME match: '{}' vs '{}', distance={}", nameA, nameB, distance);
            return Optional.of(new ResolutionDecision(
                    "FUZZY_NAME", 0.8f,
                    "Fuzzy name match: '" + nameA + "' vs '" + nameB + "' (distance=" + distance + ")",
                    a.id(), b.id()
            ));
        }
        return Optional.empty();
    }

    private Optional<ResolutionDecision> trySemanticMatch(FabricObject a, FabricObject b) {
        if (embeddingPort == null) {
            return Optional.empty();
        }

        try {
            String textA = a.properties() != null ? a.properties().toString() : "";
            String textB = b.properties() != null ? b.properties().toString() : "";

            float[] embeddingA = embeddingPort.embed(textA);
            float[] embeddingB = embeddingPort.embed(textB);

            float similarity = cosineSimilarity(embeddingA, embeddingB);

            if (similarity > 0.95f) {
                log.debug("SEMANTIC match: similarity={}", similarity);
                return Optional.of(new ResolutionDecision(
                        "SEMANTIC", similarity,
                        "Semantic similarity: " + similarity,
                        a.id(), b.id()
                ));
            }
        } catch (Exception e) {
            log.warn("Semantic matching failed: {}", e.getMessage());
        }
        return Optional.empty();
    }

    private String extractProperty(FabricObject obj, String propertyName) {
        if (obj.properties() == null) return null;
        JsonNode node = obj.properties().get(propertyName);
        return node != null && !node.isNull() ? node.asText() : null;
    }

    static int levenshteinDistance(String s1, String s2) {
        int len1 = s1.length();
        int len2 = s2.length();
        int[][] dp = new int[len1 + 1][len2 + 1];

        for (int i = 0; i <= len1; i++) dp[i][0] = i;
        for (int j = 0; j <= len2; j++) dp[0][j] = j;

        for (int i = 1; i <= len1; i++) {
            for (int j = 1; j <= len2; j++) {
                int cost = (s1.charAt(i - 1) == s2.charAt(j - 1)) ? 0 : 1;
                dp[i][j] = Math.min(
                        Math.min(dp[i - 1][j] + 1, dp[i][j - 1] + 1),
                        dp[i - 1][j - 1] + cost
                );
            }
        }

        return dp[len1][len2];
    }

    static float cosineSimilarity(float[] a, float[] b) {
        if (a.length != b.length) {
            throw new IllegalArgumentException("Vectors must have same length");
        }

        float dotProduct = 0f;
        float normA = 0f;
        float normB = 0f;

        for (int i = 0; i < a.length; i++) {
            dotProduct += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }

        float denominator = (float) (Math.sqrt(normA) * Math.sqrt(normB));
        return denominator == 0 ? 0f : dotProduct / denominator;
    }
}
