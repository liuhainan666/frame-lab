package com.framelab.user;

import java.time.LocalDateTime;

public class NewRefreshToken {

    private long id;
    private long userId;
    private String familyId;
    private String tokenHash;
    private LocalDateTime familyExpiresAt;

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public long getUserId() {
        return userId;
    }

    public void setUserId(long userId) {
        this.userId = userId;
    }

    public String getFamilyId() {
        return familyId;
    }

    public void setFamilyId(String familyId) {
        this.familyId = familyId;
    }

    public String getTokenHash() {
        return tokenHash;
    }

    public void setTokenHash(String tokenHash) {
        this.tokenHash = tokenHash;
    }

    public LocalDateTime getFamilyExpiresAt() {
        return familyExpiresAt;
    }

    public void setFamilyExpiresAt(LocalDateTime familyExpiresAt) {
        this.familyExpiresAt = familyExpiresAt;
    }
}
