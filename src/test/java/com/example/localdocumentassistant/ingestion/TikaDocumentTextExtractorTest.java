package com.example.localdocumentassistant.ingestion;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TikaDocumentTextExtractorTest {

    @TempDir
    Path tempDirectory;

    private final TikaDocumentTextExtractor extractor = new TikaDocumentTextExtractor(200_000);

    @Test
    void supportsWordDocumentTypesOnly() {
        assertThat(extractor.supports("doc")).isTrue();
        assertThat(extractor.supports("DOC")).isTrue();
        assertThat(extractor.supports("docx")).isTrue();
        assertThat(extractor.supports("DOCX")).isTrue();
        assertThat(extractor.supports("txt")).isFalse();
        assertThat(extractor.supports("pdf")).isFalse();
        assertThat(extractor.supports(null)).isFalse();
    }

    @Test
    void extractsKnownTextFromDocxFile() throws Exception {
        Path docxFile = createDocx("This Word document is about warehouse forecasting.");

        String extractedText = extractor.extract(docxFile);

        assertThat(extractedText).contains("This Word document is about warehouse forecasting.");
    }

    private Path createDocx(String text) throws Exception {
        Path path = tempDirectory.resolve("forecast.docx");
        try (XWPFDocument document = new XWPFDocument();
                OutputStream output = Files.newOutputStream(path)) {
            document.createParagraph().createRun().setText(text);
            document.write(output);
        }
        return path;
    }
}
