package com.example.localdocumentassistant.document;

import java.io.IOException;
import java.nio.file.Path;

public interface DocumentTextExtractor {

    boolean supports(String fileType);

    String extract(Path path) throws IOException;
}
