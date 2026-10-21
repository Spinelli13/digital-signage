package com.digitalsignage.backend.queue;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.digitalsignage.backend.queue.AttendanceModels.PublicQueue;
import com.digitalsignage.backend.queue.AttendanceModels.PublicTicket;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;

/** A senha pelo celular: o que a pessoa faz depois de escanear o QR Code (sem login). */
@RestController
@RequestMapping("/api/v1/public")
public class PublicQueueController {

	public record TakeRequest(@Size(max = 60) String customerName) {
	}

	private final PublicQueueService service;

	public PublicQueueController(PublicQueueService service) {
		this.service = service;
	}

	@GetMapping("/queues/{token}")
	public PublicQueue queue(@PathVariable String token) {
		return service.queue(token);
	}

	@PostMapping("/queues/{token}/tickets")
	@ResponseStatus(HttpStatus.CREATED)
	public PublicTicket take(@PathVariable String token, @Valid @RequestBody(required = false) TakeRequest request, HttpServletRequest http) {
		return service.issue(token, request == null ? null : request.customerName(), clientOf(http), http.getHeader("X-Client-Id"));
	}

	@GetMapping("/tickets/{token}")
	public PublicTicket ticket(@PathVariable String token) {
		return service.ticket(token);
	}

	@PostMapping("/tickets/{token}/cancel")
	public PublicTicket cancel(@PathVariable String token) {
		return service.cancel(token);
	}

	/** A origem de quem pede: o primeiro endereço de X-Forwarded-For (o servidor fica atrás do proxy da hospedagem) ou o da conexão. */
	static String clientOf(HttpServletRequest request) {
		String forwarded = request.getHeader("X-Forwarded-For");
		if (forwarded != null && !forwarded.isBlank()) {
			return forwarded.split(",")[0].strip();
		}
		return request.getRemoteAddr();
	}

}
