package com.example.localdocumentassistant.ingestion;

import java.io.IOException;
import java.nio.file.Path;

public interface DocumentTextExtractor {

    boolean supports(String fileType);

    String extract(Path path) throws IOException;
}
