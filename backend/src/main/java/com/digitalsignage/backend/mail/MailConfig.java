package com.digitalsignage.backend.mail;

import java.util.Properties;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;

import jakarta.mail.internet.MimeMessage;

/**
 * Escolhe o provedor pelas variáveis de ambiente: com {@code MAIL_HOST} o envio é por SMTP (serve a qualquer provedor com plano
 * gratuito: Brevo, Resend, MailerSend, Mailjet, Gmail com senha de app...); sem ele, nada é enviado.
 */
@Configuration
public class MailConfig {

	@Bean
	public EmailSender emailSender(
			@Value("${app.mail.host:}") String host,
			@Value("${app.mail.port:587}") int port,
			@Value("${app.mail.username:}") String username,
			@Value("${app.mail.password:}") String password,
			@Value("${app.mail.from:}") String from,
			@Value("${app.mail.starttls:true}") boolean startTls) {
		if (host == null || host.isBlank() || from == null || from.isBlank()) {
			return new EmailSender() {
				@Override
				public boolean enabled() {
					return false;
				}

				@Override
				public void send(Email email) {
					throw new IllegalStateException("Sem provedor de e-mail configurado");
				}
			};
		}
		return new SmtpEmailSender(host.strip(), port, username, password, from.strip(), startTls);
	}

	/** Envio por SMTP com tempos curtos (um provedor lento não segura quem pediu a recuperação de senha). */
	static final class SmtpEmailSender implements EmailSender {

		private final JavaMailSenderImpl mail = new JavaMailSenderImpl();
		private final String from;

		SmtpEmailSender(String host, int port, String username, String password, String from, boolean startTls) {
			this.from = from;
			mail.setHost(host);
			mail.setPort(port);
			if (username != null && !username.isBlank()) {
				mail.setUsername(username);
				mail.setPassword(password);
			}
			Properties properties = mail.getJavaMailProperties();
			properties.put("mail.transport.protocol", "smtp");
			properties.put("mail.smtp.auth", String.valueOf(username != null && !username.isBlank()));
			properties.put("mail.smtp.starttls.enable", String.valueOf(startTls));
			properties.put("mail.smtp.starttls.required", String.valueOf(startTls));
			properties.put("mail.smtp.connectiontimeout", "5000");
			properties.put("mail.smtp.timeout", "8000");
			properties.put("mail.smtp.writetimeout", "8000");
		}

		@Override
		public boolean enabled() {
			return true;
		}

		@Override
		public void send(Email email) {
			try {
				MimeMessage message = mail.createMimeMessage();
				MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");
				helper.setFrom(from);
				helper.setTo(email.to());
				helper.setSubject(email.subject());
				helper.setText(email.body(), false);
				mail.send(message);
			} catch (Exception e) {
				throw new IllegalStateException("Falha no envio por SMTP: " + e.getClass().getSimpleName(), e);
			}
		}
	}
}
