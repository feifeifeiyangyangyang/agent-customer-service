package com.zhifutong.customer.rag;

import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class TextChunker {

    public List<String> split(String text, int chunkSize, int overlap, int minLength) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        if (chunkSize <= 0) {
            throw new IllegalArgumentException("chunkSize must be positive");
        }
        if (overlap < 0 || overlap >= chunkSize) {
            throw new IllegalArgumentException("overlap must be >= 0 and < chunkSize");
        }

        String cleaned = clean(text);
        List<String> result = new ArrayList<>();
        int start = 0;
        while (start < cleaned.length()) {
            int end = Math.min(start + chunkSize, cleaned.length());
            int adjustedEnd = findBoundary(cleaned, start, end);
            String chunk = cleaned.substring(start, adjustedEnd).trim();
            if (chunk.length() >= minLength) {
                result.add(chunk);
            }
            if (adjustedEnd >= cleaned.length()) {
                break;
            }
            start = Math.max(0, adjustedEnd - overlap);
        }
        return result;
    }

    public String clean(String text) {
        return text.replace('\u00A0', ' ')
                .replaceAll("[\\t\\x0B\\f\\r]+", " ")
                .replaceAll(" *\\n+ *", "\n")
                .replaceAll("[ ]{2,}", " ")
                .trim();
    }

    private int findBoundary(String text, int start, int end) {
        if (end >= text.length()) {
            return text.length();
        }
        String separators = "\n。！？；;,.，、 ";
        for (int i = end; i > start + (end - start) / 2; i--) {
            if (separators.indexOf(text.charAt(i - 1)) >= 0) {
                return i;
            }
        }
        return end;
    }
}
