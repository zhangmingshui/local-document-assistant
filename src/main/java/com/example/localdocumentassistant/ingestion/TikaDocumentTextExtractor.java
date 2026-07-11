package com.example.localdocumentassistant.ingestion;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Set;

import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.sax.BodyContentHandler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.xml.sax.SAXException;

@Component
public class TikaDocumentTextExtractor implements DocumentTextExtractor {

    private static final Set<String> SUPPORTED_FILE_TYPES = Set.of("doc", "docx");
    private static final int DEFAULT_MAX_EXTRACTED_CHARACTERS = 200_000;

    private final Parser parser;
    private final int maxExtractedCharacters;

    @Autowired
    public TikaDocumentTextExtractor(
            @Value("${ingestion.text-extraction.max-characters:200000}") int maxExtractedCharacters
    ) {
        this(new AutoDetectParser(), maxExtractedCharacters);
    }

    TikaDocumentTextExtractor(Parser parser) {
        this(parser, DEFAULT_MAX_EXTRACTED_CHARACTERS);
    }

    TikaDocumentTextExtractor(Parser parser, int maxExtractedCharacters) {
        this.parser = parser;
        this.maxExtractedCharacters = maxExtractedCharacters;
    }

    @Override
    public boolean supports(String fileType) {
        return fileType != null && SUPPORTED_FILE_TYPES.contains(fileType.toLowerCase(Locale.ROOT));
    }

    @Override
    public String extract(Path path) throws IOException {
        try (InputStream inputStream = Files.newInputStream(path)) {
            BodyContentHandler handler = new BodyContentHandler(maxExtractedCharacters);
            Metadata metadata = new Metadata();
            ParseContext context = new ParseContext();
            context.set(Parser.class, parser);

            parser.parse(inputStream, handler, metadata, context);
            return handler.toString();
        } catch (TikaException | SAXException extractionError) {
            throw new IOException("Could not extract text from Word document: " + path, extractionError);
        }
    }
}
