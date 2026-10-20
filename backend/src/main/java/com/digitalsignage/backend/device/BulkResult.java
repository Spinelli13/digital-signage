package com.digitalsignage.backend.device;

/** Quantas TVs a ação realmente alcançou (ex.: "desativar" só alcança as que estavam ativas). */
public record BulkResult(int affected) {
}
