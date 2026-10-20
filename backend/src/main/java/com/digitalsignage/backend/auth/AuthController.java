package com.digitalsignage.backend.auth;

import java.util.NoSuchElementException;

import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
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

@RestController
public class AuthController {

	/** Quem está logado e em que cliente está atuando (o painel monta o menu e mostra o plano a partir daqui). */
	public record MeResponse(
			Long id,
			String name,
			String email,
			String role,
			boolean platformAdmin,
			TenantSummary tenant) {
	}

	private final UserRepository userRepository;
	private final TenantRepository tenantRepository;
	private final PasswordEncoder passwordEncoder;
	private final JwtService jwtService;
	private final TenantSummary.Factory summaries;

	public AuthController(
			UserRepository userRepository,
			TenantRepository tenantRepository,
			PasswordEncoder passwordEncoder,
			JwtService jwtService,
			TenantSummary.Factory summaries) {
		this.userRepository = userRepository;
		this.tenantRepository = tenantRepository;
		this.passwordEncoder = passwordEncoder;
		this.jwtService = jwtService;
		this.summaries = summaries;
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

	@GetMapping("/api/v1/auth/me")
	public MeResponse me(@AuthenticationPrincipal PanelUser user) {
		Tenant tenant = tenantRepository.findById(user.tenantId())
				.orElseThrow(() -> new NoSuchElementException("Cliente não encontrado"));
		return new MeResponse(user.id(), user.name(), user.email(), user.role().name(), user.isPlatformAdmin(), summaries.of(tenant));
	}

}
