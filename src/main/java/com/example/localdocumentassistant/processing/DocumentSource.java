package com.example.localdocumentassistant.processing;

public record DocumentSource(Long id, String path, boolean includeSubfolders, String status) {
}
