package com.example.localdocumentassistant.questionanswering;

import java.util.List;

public record QuestionAnsweringResult(String answer, List<QuestionSource> sources) {
}
