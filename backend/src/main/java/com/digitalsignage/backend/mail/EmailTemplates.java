package com.digitalsignage.backend.mail;

import java.time.Duration;

/** Os textos dos e-mails do sistema (português do Brasil, texto simples). Nenhum leva senha: no máximo um link que vence. */
public final class EmailTemplates {

	public record Message(String subject, String body) {
	}

	private EmailTemplates() {
	}

	public static Message passwordReset(String company, String name, String link, Duration validFor) {
		return new Message("Redefinir a sua senha do " + company,
				greeting(name) + "Recebemos um pedido para redefinir a senha da sua conta no " + company + ".\n\n"
						+ "Para escolher uma senha nova, abra o link abaixo (vale por " + hours(validFor) + " e só pode ser usado uma vez):\n\n"
						+ link + "\n\n"
						+ "Se você não pediu isso, ignore este e-mail: a sua senha continua a mesma.\n\n" + signature(company));
	}

	public static Message welcome(String company, String name, String clientName, String link, Duration validFor) {
		return new Message("Bem-vindo ao " + company,
				greeting(name) + "A sua conta no " + company + " foi criada" + (clientName == null ? "" : " para " + clientName) + ".\n\n"
						+ "Para escolher a sua senha e entrar, abra o link abaixo (vale por " + hours(validFor) + "):\n\n" + link + "\n\n"
						+ "Se o link vencer, é só usar \"Esqueci a senha\" na tela de entrada.\n\n" + signature(company));
	}

	public static Message invoice(String company, String clientName, String description, String amount, String dueDate, String paymentInstructions,
			String supportEmail) {
		StringBuilder body = new StringBuilder(greeting(clientName));
		body.append("Foi emitida uma fatura do ").append(company).append(".\n\n")
				.append("Descrição: ").append(description).append('\n')
				.append("Valor: R$ ").append(amount).append('\n')
				.append("Vencimento: ").append(dueDate).append("\n\n");
		if (paymentInstructions != null && !paymentInstructions.isBlank()) {
			body.append("Como pagar:\n").append(paymentInstructions.strip()).append("\n\n");
		}
		body.append("Depois do pagamento, o acesso segue normalmente. ");
		if (supportEmail != null && !supportEmail.isBlank()) {
			body.append("Dúvidas: ").append(supportEmail).append('.');
		}
		body.append("\n\n").append(signature(company));
		return new Message("Fatura do " + company + ": vence em " + dueDate, body.toString());
	}

	public static Message tvAlert(String company, String message, boolean resolved) {
		return new Message((resolved ? "Resolvido: " : "Alerta: ") + message,
				(resolved ? "O problema abaixo foi resolvido:\n\n" : "O " + company + " detectou um problema numa TV:\n\n") + message + "\n\n"
						+ "Veja os detalhes no painel, em Alertas.\n\n" + signature(company));
	}

	public static Message test(String company) {
		return new Message("Teste de e-mail do " + company,
				"Este é um e-mail de teste do " + company + ".\n\nSe você o recebeu, os avisos de TV offline vão chegar neste endereço.\n\n" + signature(company));
	}

	private static String greeting(String name) {
		return name == null || name.isBlank() ? "Olá,\n\n" : "Olá, " + name.strip() + ".\n\n";
	}

	private static String signature(String company) {
		return "— " + company;
	}

	private static String hours(Duration duration) {
		long hours = duration.toHours();
		return hours >= 48 ? (hours / 24) + " dias" : hours == 1 ? "1 hora" : hours + " horas";
	}
}
