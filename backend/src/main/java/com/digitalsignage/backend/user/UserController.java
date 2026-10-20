package com.digitalsignage.backend.user;

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
import com.digitalsignage.backend.common.ForbiddenException;
import com.digitalsignage.backend.security.PanelUser;
import com.digitalsignage.backend.tenant.PlanLimits;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * A equipe do cliente: quem pode entrar no painel e com que papel. Só o ADMIN do cliente (ou o operador da plataforma,
 * atuando naquele cliente). Sempre dentro do cliente em que a requisição atua.
 */
@RestController
@RequestMapping("/api/v1/users")
public class UserController {

	public static final int MIN_PASSWORD = 8;

	public record UserResponse(Long id, String name, String email, String role, boolean active, Instant createdAt) {

		static UserResponse of(User user) {
			return new UserResponse(user.getId(), user.getName(), user.getEmail(), user.getRole().name(), user.isActive(), user.getCreatedAt());
		}
	}

	public record CreateRequest(
			@NotBlank @Size(max = 150) String name,
			@NotBlank @Email @Size(max = 255) String email,
			@NotBlank String password,
			@NotNull Role role) {
	}

	/** Campos ausentes não mudam. */
	public record UpdateRequest(@Size(max = 150) String name, Role role, Boolean active, String password) {
	}

	private final UserRepository users;
	private final PasswordEncoder encoder;
	private final PlanLimits planLimits;

	public UserController(UserRepository users, PasswordEncoder encoder, PlanLimits planLimits) {
		this.users = users;
		this.encoder = encoder;
		this.planLimits = planLimits;
	}

	@GetMapping
	public List<UserResponse> list(@AuthenticationPrincipal PanelUser caller) {
		return users.findByTenantIdOrderByName(caller.tenantId()).stream().map(UserResponse::of).toList();
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	@Transactional
	public UserResponse create(@AuthenticationPrincipal PanelUser caller, @Valid @RequestBody CreateRequest request) {
		requireAssignable(caller, request.role());
		String email = request.email().trim().toLowerCase();
		if (users.existsByEmailIgnoreCase(email)) {
			throw new ConflictException("Já existe um usuário com o e-mail " + email);
		}
		requirePassword(request.password());
		planLimits.requireRoomForUser(caller.tenantId());
		User user = new User(request.name().trim(), email, encoder.encode(request.password()), caller.tenantId(), request.role());
		return UserResponse.of(users.save(user));
	}

	@PutMapping("/{id}")
	@Transactional
	public UserResponse update(@AuthenticationPrincipal PanelUser caller, @PathVariable Long id, @Valid @RequestBody UpdateRequest request) {
		User user = find(caller, id);
		requireManageable(caller, user);

		if (request.name() != null && !request.name().isBlank()) {
			user.setName(request.name().trim());
		}
		if (request.password() != null) {
			requirePassword(request.password());
			user.setPasswordHash(encoder.encode(request.password()));
		}
		boolean demoting = request.role() != null && request.role() != user.getRole();
		boolean deactivating = Boolean.FALSE.equals(request.active()) && user.isActive();
		if (demoting) {
			requireAssignable(caller, request.role());
		}
		if ((demoting || deactivating) && user.getRole().atLeast(Role.ADMIN)) {
			requireAnotherAdmin(user, caller);
		}
		if (deactivating && user.getId().equals(caller.id())) {
			throw new ConflictException("Você não pode desativar o seu próprio usuário");
		}
		if (demoting) {
			user.setRole(request.role());
		}
		if (request.active() != null) {
			if (Boolean.TRUE.equals(request.active()) && !user.isActive()) {
				planLimits.requireRoomForUser(caller.tenantId());
			}
			user.setActive(request.active());
		}
		return UserResponse.of(users.save(user));
	}

	@DeleteMapping("/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	@Transactional
	public void delete(@AuthenticationPrincipal PanelUser caller, @PathVariable Long id) {
		User user = find(caller, id);
		requireManageable(caller, user);
		if (user.getId().equals(caller.id())) {
			throw new ConflictException("Você não pode excluir o seu próprio usuário");
		}
		if (user.getRole().atLeast(Role.ADMIN) && user.isActive()) {
			requireAnotherAdmin(user, caller);
		}
		users.delete(user);
	}

	// ---------- regras ----------

	private User find(PanelUser caller, Long id) {
		return users.findByIdAndTenantId(id, caller.tenantId()).orElseThrow(() -> new NoSuchElementException("Usuário não encontrado: " + id));
	}

	/** O operador da plataforma só é mexido pelo próprio operador. */
	private static void requireManageable(PanelUser caller, User target) {
		if (target.getRole() == Role.PLATFORM_ADMIN && !caller.isPlatformAdmin()) {
			throw new ForbiddenException("Este usuário é da operação da plataforma e não pode ser alterado aqui");
		}
	}

	private static void requireAssignable(PanelUser caller, Role role) {
		if (!role.assignableByTenantAdmin() && !caller.isPlatformAdmin()) {
			throw new ForbiddenException("Este papel não pode ser dado pela equipe do cliente");
		}
	}

	/** Todo cliente precisa de pelo menos um administrador ativo, senão ninguém mais gerencia a equipe. */
	private void requireAnotherAdmin(User target, PanelUser caller) {
		long others = users.findByTenantIdOrderByName(caller.tenantId()).stream()
				.filter(user -> !user.getId().equals(target.getId()) && user.isActive() && user.getRole().atLeast(Role.ADMIN))
				.count();
		if (others == 0) {
			throw new ConflictException("Este é o único administrador ativo do cliente: promova outro usuário a Administrador antes");
		}
	}

	private static void requirePassword(String password) {
		if (password == null || password.length() < MIN_PASSWORD) {
			throw new IllegalArgumentException("A senha precisa ter pelo menos " + MIN_PASSWORD + " caracteres");
		}
	}

}
