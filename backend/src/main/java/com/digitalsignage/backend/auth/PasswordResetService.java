package com.digitalsignage.backend.auth;

import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.digitalsignage.backend.billing.PlatformSettingsRepository;
import com.digitalsignage.backend.mail.EmailService;
import com.digitalsignage.backend.mail.EmailTemplates;
import com.digitalsignage.backend.player.PanelUrl;
import com.digitalsignage.backend.security.TokenHasher;
import com.digitalsignage.backend.user.User;
import com.digitalsignage.backend.user.UserController;
import com.digitalsignage.backend.user.UserRepository;

/**
 * "Esqueci a senha" e as boas-vindas: um link com um segredo aleatório, de uso único e com prazo, que o servidor guarda só como
 * SHA-256. O pedido nunca revela se o e-mail existe (a resposta é sempre a mesma), e há um intervalo entre pedidos para ninguém
 * encher a caixa de outra pessoa.
 */
@Service
public class PasswordResetService {

	private static final Logger log = LoggerFactory.getLogger(PasswordResetService.class);

	static final Duration RESET_VALID_FOR = Duration.ofHours(1);
	static final Duration WELCOME_VALID_FOR = Duration.ofDays(3);
	/** Entre dois pedidos do mesmo e-mail. */
	static final Duration COOLDOWN = Duration.ofMinutes(2);
	static final int MAX_PER_HOUR = 5;

	private final JdbcTemplate jdbc;
	private final UserRepository users;
	private final PasswordEncoder encoder;
	private final EmailService email;
	private final PanelUrl panel;
	private final PlatformSettingsRepository settings;
	private final Clock clock;
	private final SecureRandom random = new SecureRandom();

	public PasswordResetService(JdbcTemplate jdbc, UserRepository users, PasswordEncoder encoder, EmailService email, PanelUrl panel,
			PlatformSettingsRepository settings, Clock clock) {
		this.jdbc = jdbc;
		this.users = users;
		this.encoder = encoder;
		this.email = email;
		this.panel = panel;
		this.settings = settings;
		this.clock = clock;
	}

	/** O pedido de "esqueci a senha". Silencioso por desenho: quem pede não descobre se o e-mail tem conta. */
	@Transactional
	public void request(String address) {
		Instant now = clock.instant();
		jdbc.update("DELETE FROM password_resets WHERE expires_at < ?", Timestamp.from(now.minus(Duration.ofDays(7))));
		if (address == null || address.isBlank()) {
			return;
		}
		if (!email.enabled()) {
			log.warn("Recuperação de senha pedida, mas o provedor de e-mail não está configurado: nada foi enviado");
			return;
		}
		User user = users.findByEmail(address.strip()).filter(User::isActive).orElse(null);
		if (user == null) {
			log.info("Recuperação de senha pedida para um e-mail sem conta ativa");
			return;
		}
		Integer recent = jdbc.queryForObject("SELECT count(*) FROM password_resets WHERE user_id = ? AND created_at > ?", Integer.class,
				user.getId(), Timestamp.from(now.minus(Duration.ofHours(1))));
		Integer veryRecent = jdbc.queryForObject("SELECT count(*) FROM password_resets WHERE user_id = ? AND created_at > ?", Integer.class,
				user.getId(), Timestamp.from(now.minus(COOLDOWN)));
		if ((recent != null && recent >= MAX_PER_HOUR) || (veryRecent != null && veryRecent > 0)) {
			log.info("Recuperação de senha adiada para o usuário {}: pedidos demais em pouco tempo", user.getId());
			return;
		}
		String link = link(issue(user, "RESET", RESET_VALID_FOR, now));
		if (link == null) {
			log.warn("Recuperação de senha não enviada: o endereço do painel não está configurado");
			return;
		}
		EmailTemplates.Message message = EmailTemplates.passwordReset(company(), user.getName(), link, RESET_VALID_FOR);
		email.send(user.getEmail(), message.subject(), message.body());
	}

	/** O e-mail de boas-vindas de uma conta nova, com o link para escolher a senha (vale 3 dias). */
	@Transactional
	public void welcome(User user, String clientName) {
		if (!email.enabled()) {
			return; // sem provedor, a pessoa recebe a senha provisória como sempre
		}
		String link = link(issue(user, "WELCOME", WELCOME_VALID_FOR, clock.instant()));
		if (link == null) {
			return;
		}
		EmailTemplates.Message message = EmailTemplates.welcome(company(), user.getName(), clientName, link, WELCOME_VALID_FOR);
		email.send(user.getEmail(), message.subject(), message.body());
	}

	/** Troca a senha com o link; usa-se uma vez. Link inexistente, vencido ou usado: mesma mensagem. */
	@Transactional
	public void reset(String token, String newPassword) {
		if (newPassword == null || newPassword.length() < UserController.MIN_PASSWORD) {
			throw new IllegalArgumentException("A senha nova precisa ter pelo menos " + UserController.MIN_PASSWORD + " caracteres");
		}
		Instant now = clock.instant();
		var found = token == null || token.isBlank() ? java.util.List.<Long>of() : jdbc.query(
				"SELECT user_id FROM password_resets WHERE token_hash = ? AND used_at IS NULL AND expires_at > ?",
				(rs, i) -> rs.getLong("user_id"), TokenHasher.sha256Hex(token.strip()), Timestamp.from(now));
		if (found.isEmpty()) {
			throw new IllegalArgumentException("Este link não vale mais. Peça um novo em \"Esqueci a senha\".");
		}
		User user = users.findById(found.get(0)).filter(User::isActive)
				.orElseThrow(() -> new IllegalArgumentException("Este link não vale mais. Peça um novo em \"Esqueci a senha\"."));
		user.setPasswordHash(encoder.encode(newPassword));
		user.setMustChangePassword(false);
		users.save(user);
		// o link usado e qualquer outro ainda aberto deixam de valer (quem tinha um link antigo não consegue trocar de novo)
		jdbc.update("UPDATE password_resets SET used_at = ? WHERE user_id = ? AND used_at IS NULL", Timestamp.from(now), user.getId());
	}

	private String issue(User user, String purpose, Duration validFor, Instant now) {
		byte[] raw = new byte[32];
		random.nextBytes(raw);
		String token = HexFormat.of().formatHex(raw);
		jdbc.update("INSERT INTO password_resets (user_id, token_hash, purpose, expires_at, created_at) VALUES (?, ?, ?, ?, ?)",
				user.getId(), TokenHasher.sha256Hex(token), purpose, Timestamp.from(now.plus(validFor)), Timestamp.from(now));
		return token;
	}

	private String link(String token) {
		return panel.link("/redefinir-senha/" + token);
	}

	private String company() {
		return settings.findAll().stream().findFirst().map(s -> s.getCompanyName()).filter(name -> name != null && !name.isBlank()).orElse("Vyzzu");
	}
}
