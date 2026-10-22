package com.digitalsignage.backend.mail;

/** Quem entrega o e-mail de verdade. Fica atrás de uma interface para trocar de provedor sem mexer no resto e para os testes. */
public interface EmailSender {

	/** {@code false} enquanto não há provedor configurado (nada é enviado). */
	boolean enabled();

	/** Entrega ou lança uma exceção. */
	void send(Email email);
}
