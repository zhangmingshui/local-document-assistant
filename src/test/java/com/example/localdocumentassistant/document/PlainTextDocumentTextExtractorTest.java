package com.example.localdocumentassistant.document;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PlainTextDocumentTextExtractorTest {

    @TempDir
    Path tempDirectory;

    @Test
    void extractsUtf8TextFromTxtFile() throws Exception {
        Path textFile = tempDirectory.resolve("notes.txt");
        Files.writeString(textFile, "Local notes: café", StandardCharsets.UTF_8);

        PlainTextDocumentTextExtractor extractor = new PlainTextDocumentTextExtractor();

        assertThat(extractor.supports("txt")).isTrue();
        assertThat(extractor.supports("doc")).isFalse();
        assertThat(extractor.extract(textFile)).isEqualTo("Local notes: café");
    }
}
