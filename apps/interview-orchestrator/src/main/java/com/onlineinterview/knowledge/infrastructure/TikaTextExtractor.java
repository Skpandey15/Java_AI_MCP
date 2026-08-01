package com.onlineinterview.knowledge.infrastructure;

import java.io.ByteArrayInputStream;
import org.apache.tika.Tika;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/** Extracts plain text from uploaded documents (.txt/.md/.pdf/.doc(x)/.ppt(x)/.xls(x))
 *  using Apache Tika, so binary formats can feed the RAG ingestion pipeline. */
@Component
public class TikaTextExtractor {
    private final Tika tika = new Tika();

    public TikaTextExtractor() {
        tika.setMaxStringLength(500_000);
    }

    public String extract(byte[] data) {
        try {
            return tika.parseToString(new ByteArrayInputStream(data));
        } catch (Exception exception) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Could not extract text from the uploaded file", exception);
        }
    }
}
