package ch.nexoai.fabric.engine.port.in;

import ch.nexoai.fabric.engine.model.SearchResult;

import java.util.List;

public interface SemanticSearchUseCase {
    List<SearchResult> semanticSearch(String query, String objectType, int limit, float minSimilarity);
}
