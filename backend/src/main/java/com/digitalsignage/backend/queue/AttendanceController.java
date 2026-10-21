package com.digitalsignage.backend.queue;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.digitalsignage.backend.queue.AttendanceModels.Overview;
import com.digitalsignage.backend.queue.AttendanceModels.TicketResponse;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** A tela do atendente: emitir senha, chamar a próxima, chamar de novo, atender, concluir. */
@RestController
@RequestMapping("/api/v1/attendance")
public class AttendanceController {

	public record IssueRequest(@NotNull Long queueId, @Size(max = 60) String customerName) {
	}

	private final AttendanceService service;

	public AttendanceController(AttendanceService service) {
		this.service = service;
	}

	@GetMapping("/overview")
	public Overview overview() {
		return service.overview();
	}

	@PostMapping("/tickets")
	@ResponseStatus(HttpStatus.CREATED)
	public TicketResponse issue(@Valid @RequestBody IssueRequest request) {
		return service.ticketResponse(service.issue(request.queueId(), request.customerName(), "PANEL"));
	}

	@PostMapping("/counters/{id}/call-next")
	public TicketResponse callNext(@PathVariable Long id) {
		return service.ticketResponse(service.callNext(id));
	}

	@PostMapping("/tickets/{id}/recall")
	public TicketResponse recall(@PathVariable Long id) {
		return service.ticketResponse(service.recall(id));
	}

	@PostMapping("/tickets/{id}/start")
	public TicketResponse start(@PathVariable Long id) {
		return service.ticketResponse(service.start(id));
	}

	@PostMapping("/tickets/{id}/finish")
	public TicketResponse finish(@PathVariable Long id) {
		return service.ticketResponse(service.finish(id));
	}

	@PostMapping("/tickets/{id}/no-show")
	public TicketResponse noShow(@PathVariable Long id) {
		return service.ticketResponse(service.noShow(id));
	}

	@PostMapping("/tickets/{id}/cancel")
	public TicketResponse cancel(@PathVariable Long id) {
		return service.ticketResponse(service.cancel(id));
	}

}
