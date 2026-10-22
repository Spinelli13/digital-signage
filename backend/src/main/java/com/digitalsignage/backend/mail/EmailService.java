package com.digitalsignage.backend.mail;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * O e-mail do sistema (recuperar senha, boas-vindas, fatura, TV offline), independente de provedor: quem manda de verdade é um
 * {@link EmailSender} (SMTP quando configurado). Sem provedor configurado nada é enviado e nada quebra: o sistema só registra no
 * log que o aviso não saiu. Enviar nunca derruba quem pediu: devolve se saiu.
 */
@Service
public class EmailService {

	private static final Logger log = LoggerFactory.getLogger(EmailService.class);

	private final EmailSender sender;

	public EmailService(EmailSender sender) {
		this.sender = sender;
	}

	/** Há provedor de e-mail configurado? */
	public boolean enabled() {
		return sender.enabled();
	}

	/** Manda um e-mail. {@code false} quando não há provedor, o endereço é inválido ou o provedor falhou (e o motivo vai para o log). */
	public boolean send(String to, String subject, String body) {
		if (to == null || !looksLikeAnAddress(to)) {
			log.warn("E-mail '{}' não enviado: endereço inválido", subject);
			return false;
		}
		if (!sender.enabled()) {
			log.info("E-mail '{}' para {} não enviado: o provedor de e-mail ainda não está configurado", subject, mask(to));
			return false;
		}
		try {
			sender.send(new Email(to.strip(), subject, body));
			log.info("E-mail '{}' enviado para {}", subject, mask(to));
			return true;
		} catch (RuntimeException e) {
			log.warn("E-mail '{}' para {} falhou: {}", subject, mask(to), e.toString());
			return false;
		}
	}

	/** Manda o mesmo e-mail para vários endereços; devolve quantos saíram. */
	public int sendToAll(List<String> recipients, String subject, String body) {
		int sent = 0;
		for (String to : recipients.stream().distinct().toList()) {
			sent += send(to, subject, body) ? 1 : 0;
		}
		return sent;
	}

	/** Um endereço de e-mail "razoável": o provedor faz a conferência de verdade. */
	public static boolean looksLikeAnAddress(String text) {
		String value = text.strip();
		return value.length() <= 254 && value.matches("[^@\\s,;<>]+@[^@\\s,;<>]+\\.[^@\\s,;<>]+");
	}

	/** "ana@empresa.com" vira "a**@empresa.com": o log não guarda o endereço inteiro de ninguém. */
	static String mask(String address) {
		int at = address.indexOf('@');
		return at <= 1 ? "***" + (at < 0 ? "" : address.substring(at)) : address.charAt(0) + "**" + address.substring(at);
	}
}
