package com.digitalsignage.backend.mail;

/** Um e-mail de texto simples (sem rastreadores, sem imagens): chega mais fácil e abre em qualquer leitor. */
public record Email(String to, String subject, String body) {
}
