package com.onlineinterview.knowledge.application;

import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class DocumentChunker {
    static final int MAX_CHARS = 1600;
    static final int OVERLAP_CHARS = 200;

    public List<String> chunk(String input) {
        var normalized = input.replace("\r\n", "\n").replace('\r', '\n').trim();
        if (normalized.isEmpty()) return List.of();
        var chunks = new ArrayList<String>();
        int start = 0;
        while (start < normalized.length()) {
            int hardEnd = Math.min(start + MAX_CHARS, normalized.length());
            int end = hardEnd;
            if (hardEnd < normalized.length()) {
                int paragraph = normalized.lastIndexOf("\n\n", hardEnd);
                int sentence = normalized.lastIndexOf(". ", hardEnd);
                int boundary = Math.max(paragraph, sentence < 0 ? -1 : sentence + 1);
                if (boundary > start + MAX_CHARS / 2) end = boundary;
            }
            chunks.add(normalized.substring(start, end).trim());
            if (end == normalized.length()) break;
            start = Math.max(end - OVERLAP_CHARS, start + 1);
        }
        return List.copyOf(chunks);
    }
}
