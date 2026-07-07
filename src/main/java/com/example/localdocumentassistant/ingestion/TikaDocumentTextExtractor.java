package com.example.localdocumentassistant.ingestion;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Set;

import org.apache.tika.Tika;
import org.apache.tika.exception.TikaException;
import org.springframework.stereotype.Component;

@Component
public class TikaDocumentTextExtractor implements DocumentTextExtractor {

    private static final Set<String> SUPPORTED_FILE_TYPES = Set.of("doc", "docx");

    private final Tika tika;

    public TikaDocumentTextExtractor() {
        this(new Tika());
    }

    TikaDocumentTextExtractor(Tika tika) {
        this.tika = tika;
    }

    @Override
    public boolean supports(String fileType) {
        return fileType != null && SUPPORTED_FILE_TYPES.contains(fileType.toLowerCase(Locale.ROOT));
    }

    @Override
    public String extract(Path path) throws IOException {
        try (InputStream inputStream = Files.newInputStream(path)) {
            return tika.parseToString(inputStream);
        } catch (TikaException extractionError) {
            throw new IOException("Could not extract text from Word document: " + path, extractionError);
        }
    }
}
