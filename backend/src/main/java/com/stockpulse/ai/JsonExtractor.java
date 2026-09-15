package com.stockpulse.ai;

/**
 * LLMs are asked to return a bare JSON object but sometimes wrap it in a markdown code fence
 * or add a stray sentence. This pulls out the outermost {...} object defensively rather than
 * trusting the provider to follow instructions exactly.
 */
public final class JsonExtractor {

    private JsonExtractor() {
    }

    public static String extractJsonObject(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new AiUnavailableException("Empty response from LLM provider");
        }
        int start = raw.indexOf('{');
        int end = raw.lastIndexOf('}');
        if (start < 0 || end < start) {
            throw new AiUnavailableException("No JSON object found in LLM response");
        }
        return raw.substring(start, end + 1);
    }
}
