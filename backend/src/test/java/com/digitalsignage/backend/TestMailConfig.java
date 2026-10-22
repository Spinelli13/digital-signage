package com.digitalsignage.backend;

import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import com.digitalsignage.backend.mail.Email;
import com.digitalsignage.backend.mail.EmailSender;

/** Os testes nunca mandam e-mail de verdade: tudo o que o sistema "enviaria" fica numa lista para o teste conferir. */
@TestConfiguration
public class TestMailConfig {

	@Bean
	@Primary
	public RecordingEmailSender recordingEmailSender() {
		return new RecordingEmailSender();
	}

	public static class RecordingEmailSender implements EmailSender {

		private final List<Email> sent = new ArrayList<>();
		private boolean enabled = true;
		private boolean failing;

		public void reset() {
			sent.clear();
			enabled = true;
			failing = false;
		}

		public void enabled(boolean value) {
			this.enabled = value;
		}

		public void failing(boolean value) {
			this.failing = value;
		}

		public List<Email> sent() {
			return sent;
		}

		/** O último e-mail enviado para o endereço (nulo se não houve). */
		public Email lastTo(String address) {
			for (int i = sent.size() - 1; i >= 0; i--) {
				if (sent.get(i).to().equalsIgnoreCase(address)) {
					return sent.get(i);
				}
			}
			return null;
		}

		@Override
		public boolean enabled() {
			return enabled;
		}

		@Override
		public void send(Email email) {
			if (failing) {
				throw new IllegalStateException("provedor fora do ar");
			}
			sent.add(email);
		}
	}
}
