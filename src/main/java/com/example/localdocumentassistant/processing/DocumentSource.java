package com.example.localdocumentassistant.processing;

public record DocumentSource(String path, boolean includeSubfolders, String status) {
}
