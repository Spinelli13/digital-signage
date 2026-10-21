package com.digitalsignage.backend.platform;

import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.digitalsignage.backend.common.ConflictException;
import com.digitalsignage.backend.security.PanelUser;
import com.digitalsignage.backend.user.Role;
import com.digitalsignage.backend.user.User;
import com.digitalsignage.backend.user.UserController;
import com.digitalsignage.backend.user.UserRepository;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Os gestores da plataforma: quem opera o serviço (clientes, planos, cupons e faturas). Só um gestor cria, altera e
 * remove outro. Cada gestor entra com o próprio e-mail e senha, então o sócio não precisa dividir o login.
 */
@RestController
@RequestMapping("/api/v1/platform/managers")
public class PlatformManagerController {

	public record ManagerResponse(Long id, String name, String email, boolean active, Instant createdAt) {

		static ManagerResponse of(User user) {
			return new ManagerResponse(user.getId(), user.getName(), user.getEmail(), user.isActive(), user.getCreatedAt());
		}
	}

	public record CreateRequest(
			@NotBlank @Size(max = 150) String name,
			@NotBlank @Email @Size(max = 255) String email,
			@NotBlank String password) {
	}

	/** Campos ausentes não mudam. */
	public record UpdateRequest(@Size(max = 150) String name, Boolean active, String password) {
	}

	private final UserRepository users;
	private final PasswordEncoder encoder;

	public PlatformManagerController(UserRepository users, PasswordEncoder encoder) {
		this.users = users;
		this.encoder = encoder;
	}

	@GetMapping
	public List<ManagerResponse> list() {
		return users.findByRoleOrderByName(Role.PLATFORM_ADMIN.name()).stream().map(ManagerResponse::of).toList();
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	@Transactional
	public ManagerResponse create(@AuthenticationPrincipal PanelUser caller, @Valid @RequestBody CreateRequest request) {
		String email = request.email().trim().toLowerCase();
		if (users.existsByEmailIgnoreCase(email)) {
			throw new ConflictException("Já existe um usuário com o e-mail " + email);
		}
		requirePassword(request.password());
		// o gestor mora no mesmo cliente de quem o cadastrou (o cliente da própria operação)
		User manager = new User(request.name().trim(), email, encoder.encode(request.password()), caller.homeTenantId(), Role.PLATFORM_ADMIN);
		return ManagerResponse.of(users.save(manager));
	}

	@PutMapping("/{id}")
	@Transactional
	public ManagerResponse update(@AuthenticationPrincipal PanelUser caller, @PathVariable Long id, @Valid @RequestBody UpdateRequest request) {
		User manager = find(id);
		if (request.name() != null && !request.name().isBlank()) {
			manager.setName(request.name().trim());
		}
		if (request.password() != null) {
			requirePassword(request.password());
			manager.setPasswordHash(encoder.encode(request.password()));
		}
		if (request.active() != null) {
			if (!request.active() && manager.getId().equals(caller.id())) {
				throw new ConflictException("Você não pode desativar o seu próprio usuário");
			}
			manager.setActive(request.active());
		}
		return ManagerResponse.of(users.save(manager));
	}

	@DeleteMapping("/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	@Transactional
	public void delete(@AuthenticationPrincipal PanelUser caller, @PathVariable Long id) {
		User manager = find(id);
		if (manager.getId().equals(caller.id())) {
			throw new ConflictException("Você não pode excluir o seu próprio usuário");
		}
		users.delete(manager);
	}

	private User find(Long id) {
		return users.findById(id).filter(user -> user.getRole() == Role.PLATFORM_ADMIN)
				.orElseThrow(() -> new NoSuchElementException("Gestor não encontrado: " + id));
	}

	private static void requirePassword(String password) {
		if (password == null || password.length() < UserController.MIN_PASSWORD) {
			throw new IllegalArgumentException("A senha precisa ter pelo menos " + UserController.MIN_PASSWORD + " caracteres");
		}
	}

}
