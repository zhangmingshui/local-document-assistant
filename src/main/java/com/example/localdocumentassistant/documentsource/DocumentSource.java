package com.example.localdocumentassistant.documentsource;

public record DocumentSource(Long id, String path, boolean includeSubfolders, String status) {
}
