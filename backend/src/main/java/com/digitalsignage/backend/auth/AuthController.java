package com.digitalsignage.backend.auth;

import java.util.NoSuchElementException;

import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.digitalsignage.backend.security.JwtService;
import com.digitalsignage.backend.security.PanelUser;
import com.digitalsignage.backend.tenant.Tenant;
import com.digitalsignage.backend.tenant.TenantRepository;
import com.digitalsignage.backend.tenant.TenantSummary;
import com.digitalsignage.backend.user.User;
import com.digitalsignage.backend.user.UserRepository;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

@RestController
public class AuthController {

	/** Quem está logado e em que cliente está atuando (o painel monta o menu e mostra o plano a partir daqui). */
	public record MeResponse(
			Long id,
			String name,
			String email,
			String role,
			boolean platformAdmin,
			/** Senha provisória: o painel pede a troca antes de qualquer outra coisa. */
			boolean mustChangePassword,
			TenantSummary tenant) {
	}

	public record PasswordChange(@NotBlank String currentPassword, @NotBlank String newPassword) {
	}

	public record ForgotPassword(@NotBlank @jakarta.validation.constraints.Size(max = 255) String email) {
	}

	public record ResetPassword(@NotBlank String token, @NotBlank String newPassword) {
	}

	private final UserRepository userRepository;
	private final TenantRepository tenantRepository;
	private final PasswordEncoder passwordEncoder;
	private final JwtService jwtService;
	private final TenantSummary.Factory summaries;
	private final PasswordResetService passwordResets;

	public AuthController(
			UserRepository userRepository,
			TenantRepository tenantRepository,
			PasswordEncoder passwordEncoder,
			JwtService jwtService,
			TenantSummary.Factory summaries,
			PasswordResetService passwordResets) {
		this.userRepository = userRepository;
		this.tenantRepository = tenantRepository;
		this.passwordEncoder = passwordEncoder;
		this.jwtService = jwtService;
		this.summaries = summaries;
		this.passwordResets = passwordResets;
	}

	@PostMapping("/api/v1/auth/login")
	public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
		User user = userRepository.findByEmail(request.email())
				.filter(User::isActive)
				.orElseThrow(() -> new BadCredentialsException("E-mail ou senha inválidos"));

		if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
			throw new BadCredentialsException("E-mail ou senha inválidos");
		}

		return ResponseEntity.ok(new LoginResponse(jwtService.generateToken(user.getEmail())));
	}

	/** "Esqueci a senha": a resposta é sempre a mesma, exista ou não uma conta com o e-mail (ninguém descobre quem é cliente). */
	@PostMapping("/api/v1/auth/forgot-password")
	public ResponseEntity<Void> forgotPassword(@Valid @RequestBody ForgotPassword request) {
		passwordResets.request(request.email());
		return ResponseEntity.noContent().build();
	}

	/** Escolhe a senha nova com o link que chegou por e-mail (uso único, com prazo). */
	@PostMapping("/api/v1/auth/reset-password")
	public ResponseEntity<Void> resetPassword(@Valid @RequestBody ResetPassword request) {
		passwordResets.reset(request.token(), request.newPassword());
		return ResponseEntity.noContent().build();
	}

	/** Cada pessoa troca a própria senha (precisa da atual, para que um computador esquecido logado não a troque por ela). */
	@PutMapping("/api/v1/auth/password")
	@org.springframework.transaction.annotation.Transactional
	public ResponseEntity<Void> changePassword(@AuthenticationPrincipal PanelUser current, @Valid @RequestBody PasswordChange request) {
		User user = userRepository.findById(current.id()).orElseThrow(() -> new NoSuchElementException("Usuário não encontrado"));
		if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
			throw new IllegalArgumentException("A senha atual não confere");
		}
		if (request.newPassword().length() < com.digitalsignage.backend.user.UserController.MIN_PASSWORD) {
			throw new IllegalArgumentException("A senha nova precisa ter pelo menos " + com.digitalsignage.backend.user.UserController.MIN_PASSWORD + " caracteres");
		}
		user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
		user.setMustChangePassword(false);
		userRepository.save(user);
		return ResponseEntity.noContent().build();
	}

	@GetMapping("/api/v1/auth/me")
	public MeResponse me(@AuthenticationPrincipal PanelUser user) {
		Tenant tenant = tenantRepository.findById(user.tenantId())
				.orElseThrow(() -> new NoSuchElementException("Cliente não encontrado"));
		boolean mustChange = userRepository.findById(user.id()).map(User::isMustChangePassword).orElse(false);
		return new MeResponse(user.id(), user.name(), user.email(), user.role().name(), user.isPlatformAdmin(), mustChange, summaries.of(tenant));
	}

}
