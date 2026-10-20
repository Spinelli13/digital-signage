package com.digitalsignage.backend.media;

/** requiresAuth = true quando a URL aponta para o próprio backend e exige o JWT do painel. */
public record MediaUrlResponse(String url, boolean requiresAuth) {
}
