package com.digitalsignage.backend.user;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class AdminUserSeeder implements CommandLineRunner {

	private final UserRepository userRepository;
	private final PasswordEncoder passwordEncoder;
	private final String adminName;
	private final String adminEmail;
	private final String adminPassword;

	public AdminUserSeeder(
			UserRepository userRepository,
			PasswordEncoder passwordEncoder,
			@Value("${app.admin.name}") String adminName,
			@Value("${app.admin.email}") String adminEmail,
			@Value("${app.admin.password}") String adminPassword) {
		this.userRepository = userRepository;
		this.passwordEncoder = passwordEncoder;
		this.adminName = adminName;
		this.adminEmail = adminEmail;
		this.adminPassword = adminPassword;
	}

	@Override
	public void run(String... args) {
		if (userRepository.findByEmail(adminEmail).isEmpty()) {
			userRepository.save(new User(adminName, adminEmail, passwordEncoder.encode(adminPassword)));
		}
	}

}
