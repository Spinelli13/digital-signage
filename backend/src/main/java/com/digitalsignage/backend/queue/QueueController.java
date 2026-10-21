package com.digitalsignage.backend.queue;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.digitalsignage.backend.queue.AttendanceModels.CounterResponse;
import com.digitalsignage.backend.queue.AttendanceModels.QueueResponse;

import jakarta.validation.Valid;

/** Cadastro das filas e dos guichês (consultar: qualquer papel; mexer: o Administrador). */
@RestController
@RequestMapping("/api/v1")
public class QueueController {

	private final AttendanceService service;

	public QueueController(AttendanceService service) {
		this.service = service;
	}

	@GetMapping("/queues")
	public List<QueueResponse> queues() {
		return service.listQueues();
	}

	@PostMapping("/queues")
	@ResponseStatus(HttpStatus.CREATED)
	public QueueResponse createQueue(@Valid @RequestBody AttendanceService.QueueRequest request) {
		return asResponse(service.createQueue(request));
	}

	@PutMapping("/queues/{id}")
	public QueueResponse updateQueue(@PathVariable Long id, @Valid @RequestBody AttendanceService.QueueRequest request) {
		return asResponse(service.updateQueue(id, request));
	}

	@DeleteMapping("/queues/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void deleteQueue(@PathVariable Long id) {
		service.deleteQueue(id);
	}

	@GetMapping("/counters")
	public List<CounterResponse> counters() {
		return service.listCounters();
	}

	@PostMapping("/counters")
	@ResponseStatus(HttpStatus.CREATED)
	public CounterResponse createCounter(@Valid @RequestBody AttendanceService.CounterRequest request) {
		ServiceCounter counter = service.createCounter(request);
		return new CounterResponse(counter.getId(), counter.getName(), counter.getQueueIds(), counter.isActive(), null);
	}

	@PutMapping("/counters/{id}")
	public CounterResponse updateCounter(@PathVariable Long id, @Valid @RequestBody AttendanceService.CounterRequest request) {
		ServiceCounter counter = service.updateCounter(id, request);
		return new CounterResponse(counter.getId(), counter.getName(), counter.getQueueIds(), counter.isActive(), null);
	}

	@DeleteMapping("/counters/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void deleteCounter(@PathVariable Long id) {
		service.deleteCounter(id);
	}

	private static QueueResponse asResponse(ServiceQueue queue) {
		return new QueueResponse(queue.getId(), queue.getName(), queue.getPrefix(), queue.isPriority(), queue.isActive(), queue.getTimezone(), 0);
	}

}
