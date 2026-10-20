package com.digitalsignage.backend.security;

import java.io.IOException;
import java.util.List;

import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.digitalsignage.backend.device.Device;
import com.digitalsignage.backend.device.DeviceRepository;
import com.digitalsignage.backend.device.DeviceStatus;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Autentica o Player pelo header {@code X-Device-Token}. O principal é o id do dispositivo.
 * Dispositivo ativo ganha ROLE_DEVICE, pendente ganha ROLE_DEVICE_PENDING e desativado não autentica
 * (o endpoint protegido responde 401 e o Player para de tentar).
 */
@Component
public class DeviceAuthFilter extends OncePerRequestFilter {

	public static final String HEADER = "X-Device-Token";

	private final DeviceRepository deviceRepository;

	public DeviceAuthFilter(DeviceRepository deviceRepository) {
		this.deviceRepository = deviceRepository;
	}

	@Override
	protected void doFilterInternal(
			@NonNull HttpServletRequest request,
			@NonNull HttpServletResponse response,
			@NonNull FilterChain filterChain) throws ServletException, IOException {

		String token = request.getHeader(HEADER);
		if (token != null && !token.isBlank() && SecurityContextHolder.getContext().getAuthentication() == null) {
			deviceRepository.findByDeviceTokenHash(TokenHasher.sha256Hex(token)).ifPresent(this::authenticate);
		}

		filterChain.doFilter(request, response);
	}

	private void authenticate(Device device) {
		String role = switch (DeviceStatus.valueOf(device.getStatus())) {
			case ACTIVE -> "ROLE_DEVICE";
			case PENDING -> "ROLE_DEVICE_PENDING";
			case DISABLED -> null;
		};

		if (role != null) {
			var authentication = new UsernamePasswordAuthenticationToken(
					device.getId(), null, List.of(new SimpleGrantedAuthority(role)));
			SecurityContextHolder.getContext().setAuthentication(authentication);
		}
	}

}
