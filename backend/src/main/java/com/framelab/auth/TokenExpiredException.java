package com.framelab.auth;

public class TokenExpiredException extends RuntimeException {

    public TokenExpiredException() {
        super("token expired", null, false, false);
    }
}
