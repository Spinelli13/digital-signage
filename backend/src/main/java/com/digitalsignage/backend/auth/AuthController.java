package com.digitalsignage.backend.auth;

import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.digitalsignage.backend.security.JwtService;
import com.digitalsignage.backend.user.User;
import com.digitalsignage.backend.user.UserRepository;

import jakarta.validation.Valid;

@RestController
public class AuthController {

	private final UserRepository userRepository;
	private final PasswordEncoder passwordEncoder;
	private final JwtService jwtService;

	public AuthController(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtService jwtService) {
		this.userRepository = userRepository;
		this.passwordEncoder = passwordEncoder;
		this.jwtService = jwtService;
	}

	@PostMapping("/api/v1/auth/login")
	public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
		User user = userRepository.findByEmail(request.email())
				.orElseThrow(() -> new BadCredentialsException("E-mail ou senha inválidos"));

		if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
			throw new BadCredentialsException("E-mail ou senha inválidos");
		}

		return ResponseEntity.ok(new LoginResponse(jwtService.generateToken(user.getEmail())));
	}

}
