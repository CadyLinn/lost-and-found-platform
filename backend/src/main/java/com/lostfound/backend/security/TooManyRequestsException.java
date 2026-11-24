package com.lostfound.backend.security;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** HTTP 429, with a Retry-After header telling the client how many seconds to wait. */
public class TooManyRequestsException extends ResponseStatusException {

    private final long retryAfterSeconds;

    public TooManyRequestsException(String reason, long retryAfterSeconds) {
        super(HttpStatus.TOO_MANY_REQUESTS, reason);
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public long retryAfterSeconds() {
        return retryAfterSeconds;
    }

    @Override
    public HttpHeaders getHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.RETRY_AFTER, String.valueOf(retryAfterSeconds));
        return headers;
    }
}
