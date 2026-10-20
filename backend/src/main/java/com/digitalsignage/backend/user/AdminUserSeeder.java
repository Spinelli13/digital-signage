package com.digitalsignage.backend.user;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import com.digitalsignage.backend.tenant.Tenant;
import com.digitalsignage.backend.tenant.TenantRepository;

/**
 * Garante o primeiro usuário: o operador da plataforma (PLATFORM_ADMIN), que mora no cliente original da
 * instalação ("minha-empresa") e pode entrar em qualquer outro.
 */
@Component
public class AdminUserSeeder implements CommandLineRunner {

	public static final String DEFAULT_TENANT_SLUG = "minha-empresa";

	private final UserRepository userRepository;
	private final TenantRepository tenantRepository;
	private final PasswordEncoder passwordEncoder;
	private final String adminName;
	private final String adminEmail;
	private final String adminPassword;

	public AdminUserSeeder(
			UserRepository userRepository,
			TenantRepository tenantRepository,
			PasswordEncoder passwordEncoder,
			@Value("${app.admin.name}") String adminName,
			@Value("${app.admin.email}") String adminEmail,
			@Value("${app.admin.password}") String adminPassword) {
		this.userRepository = userRepository;
		this.tenantRepository = tenantRepository;
		this.passwordEncoder = passwordEncoder;
		this.adminName = adminName;
		this.adminEmail = adminEmail;
		this.adminPassword = adminPassword;
	}

	@Override
	public void run(String... args) {
		if (userRepository.findByEmail(adminEmail).isEmpty()) {
			Tenant home = tenantRepository.findBySlug(DEFAULT_TENANT_SLUG)
					.orElseThrow(() -> new IllegalStateException("O cliente original da instalação não existe (migration V7)"));
			userRepository.save(new User(adminName, adminEmail, passwordEncoder.encode(adminPassword), home.getId(), Role.PLATFORM_ADMIN));
		}
	}

}
