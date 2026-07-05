package com.example.localdocumentassistant.questionanswering;

public record QuestionSource(String fileName, String filePath, int chunkNumber, String text) {
}
