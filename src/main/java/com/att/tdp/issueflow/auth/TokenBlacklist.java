package com.att.tdp.issueflow.auth;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class TokenBlacklist {

    private final ConcurrentHashMap<String, Instant> blacklisted = new ConcurrentHashMap<>();

    public void add(String jti, Instant expiry) {
        blacklisted.put(jti, expiry);
    }

    public boolean isBlacklisted(String jti) {
        Instant expiry = blacklisted.get(jti);
        if (expiry == null) return false;
        if (Instant.now().isAfter(expiry)) {
            blacklisted.remove(jti);
            return false;
        }
        return true;
    }
}
