package com.example.localdocumentassistant.document;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.springframework.stereotype.Component;

@Component
public class PlainTextDocumentTextExtractor implements DocumentTextExtractor {

    @Override
    public boolean supports(String fileType) {
        return "txt".equalsIgnoreCase(fileType);
    }

    @Override
    public String extract(Path path) throws IOException {
        return Files.readString(path, StandardCharsets.UTF_8);
    }
}
