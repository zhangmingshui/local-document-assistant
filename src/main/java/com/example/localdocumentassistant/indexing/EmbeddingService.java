package com.example.localdocumentassistant.indexing;

import java.util.List;

public interface EmbeddingService {

    List<Double> embed(String text);
}
