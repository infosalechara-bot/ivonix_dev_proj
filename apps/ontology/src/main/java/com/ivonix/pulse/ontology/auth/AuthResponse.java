package com.ivonix.pulse.ontology.auth;

public record AuthResponse(String accessToken, String refreshToken, long expiresInSeconds) {}
