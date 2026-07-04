package com.example.localdocumentassistant.source;

public record DocumentSource(Long id, String path, boolean includeSubfolders, String status) {
}
