package com.digitalsignage.backend.common;

import java.util.Map;
import java.util.NoSuchElementException;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {

	@ExceptionHandler(BadCredentialsException.class)
	public ResponseEntity<Map<String, String>> handleBadCredentials(BadCredentialsException ex) {
		return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", ex.getMessage()));
	}

	@ExceptionHandler(NoSuchElementException.class)
	public ResponseEntity<Map<String, String>> handleNotFound(NoSuchElementException ex) {
		return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", ex.getMessage()));
	}

	@ExceptionHandler(IllegalArgumentException.class)
	public ResponseEntity<Map<String, String>> handleIllegalArgument(IllegalArgumentException ex) {
		return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
	}

	@ExceptionHandler(ConflictException.class)
	public ResponseEntity<Map<String, String>> handleConflict(ConflictException ex) {
		return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", ex.getMessage()));
	}

	@ExceptionHandler(RateLimitException.class)
	public ResponseEntity<Map<String, String>> handleRateLimit(RateLimitException ex) {
		return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(Map.of("error", ex.getMessage()));
	}

	/** O papel do usuário não permite a operação. */
	@ExceptionHandler(ForbiddenException.class)
	public ResponseEntity<Map<String, String>> handleForbidden(ForbiddenException ex) {
		return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", ex.getMessage()));
	}

	/** O plano do cliente não permite (limite de TVs, de espaço ou de usuários). */
	@ExceptionHandler(com.digitalsignage.backend.tenant.PlanLimitException.class)
	public ResponseEntity<Map<String, String>> handlePlanLimit(com.digitalsignage.backend.tenant.PlanLimitException ex) {
		return ResponseEntity.status(HttpStatus.PAYMENT_REQUIRED).body(Map.of("error", ex.getMessage()));
	}

	/**
	 * O armazenamento de arquivos (S3/Supabase) recusou o envio: o motivo mais comum é o arquivo passar do limite do bucket ou ter
	 * um tipo que ele não aceita. Sem isto a pessoa só via "Internal Server Error".
	 */
	@ExceptionHandler(software.amazon.awssdk.services.s3.model.S3Exception.class)
	public ResponseEntity<Map<String, String>> handleStorage(software.amazon.awssdk.services.s3.model.S3Exception ex) {
		int status = ex.statusCode();
		String detail = ex.awsErrorDetails() != null && ex.awsErrorDetails().errorMessage() != null ? ex.awsErrorDetails().errorMessage() : ex.getMessage();
		if (status == 413) {
			return ResponseEntity.badRequest().body(Map.of("error",
					"O arquivo passa do limite do armazenamento (50 MB no plano gratuito). Corte o vídeo ou grave em resolução menor."));
		}
		if (status == 415) {
			return ResponseEntity.badRequest().body(Map.of("error",
					"O armazenamento de arquivos não aceita este tipo de arquivo (" + detail + "). Use imagens JPG, PNG ou WEBP e vídeos MP4 ou MOV."));
		}
		if (status >= 400 && status < 500 && status != 401 && status != 403) {
			return ResponseEntity.badRequest().body(Map.of("error", "O armazenamento de arquivos recusou o envio: " + detail));
		}
		throw ex;
	}

	@ExceptionHandler(DataIntegrityViolationException.class)
	public ResponseEntity<Map<String, String>> handleDataIntegrityViolation(DataIntegrityViolationException ex) {
		return ResponseEntity.status(HttpStatus.CONFLICT)
				.body(Map.of("error", "Não é possível remover: existe outro registro que depende deste item"));
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<Map<String, String>> handleValidation(MethodArgumentNotValidException ex) {
		String message = ex.getBindingResult().getFieldErrors().stream()
				.findFirst()
				.map(error -> error.getField() + ": " + error.getDefaultMessage())
				.orElse("Dados inválidos");
		return ResponseEntity.badRequest().body(Map.of("error", message));
	}

}
