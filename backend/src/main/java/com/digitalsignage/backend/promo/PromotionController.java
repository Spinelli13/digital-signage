package com.digitalsignage.backend.promo;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.digitalsignage.backend.promo.PromotionModels.CouponLookup;
import com.digitalsignage.backend.promo.PromotionModels.CouponResponse;
import com.digitalsignage.backend.promo.PromotionModels.PromotionRequest;
import com.digitalsignage.backend.promo.PromotionModels.PromotionResponse;
import com.digitalsignage.backend.promo.PromotionModels.RedeemRequest;
import com.digitalsignage.backend.security.PanelUser;
import com.digitalsignage.backend.user.Role;

import jakarta.validation.Valid;

/**
 * Os cupons para os clientes da loja, no painel: montar as promoções (Editor) e dar baixa no caixa (Editor). Consultar vale para
 * qualquer papel, mas o segredo do QR Code só vai para quem edita. O recurso é opcional do plano (COUPONS).
 */
@RestController
@RequestMapping("/api/v1/promotions")
public class PromotionController {

	private final PromotionService service;

	public PromotionController(PromotionService service) {
		this.service = service;
	}

	@GetMapping
	public List<PromotionResponse> list(@AuthenticationPrincipal PanelUser user) {
		List<PromotionResponse> all = service.list();
		return user.role().atLeast(Role.EDITOR) ? all : all.stream().map(PromotionResponse::withoutToken).toList();
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public PromotionResponse create(@Valid @RequestBody PromotionRequest request) {
		return service.create(request);
	}

	@PutMapping("/{id}")
	public PromotionResponse update(@PathVariable Long id, @Valid @RequestBody PromotionRequest request) {
		return service.update(id, request);
	}

	@DeleteMapping("/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void delete(@PathVariable Long id) {
		service.delete(id);
	}

	@GetMapping("/{id}/coupons")
	public List<CouponResponse> coupons(@PathVariable Long id) {
		return service.couponsOf(id);
	}

	/** Confere um código no caixa sem dar baixa. */
	@GetMapping("/coupons/lookup")
	public CouponLookup lookup(@RequestParam String code) {
		return service.lookup(code);
	}

	/** Dá baixa: o cupom só vale uma vez. */
	@PostMapping("/coupons/redeem")
	public CouponLookup redeem(@AuthenticationPrincipal PanelUser user, @Valid @RequestBody RedeemRequest request) {
		return service.redeem(request.code(), user.email());
	}

}
