package com.stockpulse.ai;

/**
 * Raised for any AI failure mode: network error, timeout, quota, unparseable JSON, or a
 * response that fails sanity validation. Callers catch this and fall back to the rule-based
 * strategy rather than letting the failure propagate.
 */
public class AiUnavailableException extends RuntimeException {
    public AiUnavailableException(String message) {
        super(message);
    }

    public AiUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
