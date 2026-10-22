package com.digitalsignage.backend.promo;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.digitalsignage.backend.promo.PromotionModels.ClaimRequest;
import com.digitalsignage.backend.promo.PromotionModels.PublicCoupon;
import com.digitalsignage.backend.promo.PromotionModels.PublicPromotion;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

/** O cupom pelo celular: o que o consumidor faz depois de escanear o QR Code da TV (sem login). */
@RestController
@RequestMapping("/api/v1/public")
public class PublicPromotionController {

	private final PublicPromotionService service;

	public PublicPromotionController(PublicPromotionService service) {
		this.service = service;
	}

	/** {@code o}: de onde veio o QR Code ("tv-12", "balcao"); cada abertura da página conta como um escaneamento (sem repetir o mesmo aparelho por alguns minutos). */
	@GetMapping("/promotions/{token}")
	public PublicPromotion promotion(@PathVariable String token, @RequestParam(name = "o", required = false) String origin, HttpServletRequest http) {
		return service.promotion(token, origin, http.getHeader("X-Client-Id"), network(http));
	}

	@PostMapping("/promotions/{token}/coupons")
	@ResponseStatus(HttpStatus.CREATED)
	public PublicCoupon claim(@PathVariable String token, @Valid @RequestBody(required = false) ClaimRequest request, HttpServletRequest http) {
		return service.claim(token, request == null ? null : request.customerName(), request != null && Boolean.TRUE.equals(request.consent()), network(http),
				http.getHeader("X-Client-Id"), request == null ? null : request.origin());
	}

	@GetMapping("/coupons/{token}")
	public PublicCoupon coupon(@PathVariable String token) {
		return service.coupon(token);
	}

	/** A origem de quem pede: o primeiro endereço de X-Forwarded-For (o servidor fica atrás do proxy da hospedagem) ou o da conexão. */
	private static String network(HttpServletRequest request) {
		String forwarded = request.getHeader("X-Forwarded-For");
		if (forwarded != null && !forwarded.isBlank()) {
			return forwarded.split(",")[0].strip();
		}
		return request.getRemoteAddr();
	}

}
