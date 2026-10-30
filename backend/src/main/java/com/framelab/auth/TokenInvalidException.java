package com.framelab.auth;

public class TokenInvalidException extends RuntimeException {

    public TokenInvalidException() {
        super("token invalid", null, false, false);
    }
}
