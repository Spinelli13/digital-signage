package com.digitalsignage.backend.platform;

import java.util.List;
import java.util.NoSuchElementException;

import org.springframework.http.HttpStatus;
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
import com.digitalsignage.backend.plan.Feature;
import com.digitalsignage.backend.plan.Plan;
import com.digitalsignage.backend.plan.PlanRepository;
import com.digitalsignage.backend.tenant.TenantRepository;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Os planos que a operação oferece (limites e preço). */
@RestController
@RequestMapping("/api/v1/platform/plans")
public class PlatformPlanController {

	public record PlanRequest(
			@NotBlank @Size(min = 2, max = 40) @Pattern(regexp = "[a-z0-9-]+", message = "use só letras minúsculas, números e hífen") String code,
			@NotBlank @Size(max = 100) String name,
			@Size(max = 500) String description,
			@Min(0) int priceCents,
			@Min(0) Integer maxDevices,
			@Min(0) Integer maxStorageMb,
			@Min(0) Integer maxUsers,
			@Min(1) Integer maxUploadMb,
			/** Recursos incluídos. Ausente: na criação vale tudo; na edição, não muda. */
			List<Feature> features,
			Boolean active,
			Integer sortOrder) {
	}

	public record PlanResponse(
			Long id, String code, String name, String description, int priceCents, String currency,
			Integer maxDevices, Integer maxStorageMb, Integer maxUsers, Integer maxUploadMb, List<String> features, boolean active, int sortOrder,
			long tenantCount) {
	}

	private final PlanRepository plans;
	private final TenantRepository tenants;

	public PlatformPlanController(PlanRepository plans, TenantRepository tenants) {
		this.plans = plans;
		this.tenants = tenants;
	}

	@GetMapping
	public List<PlanResponse> list() {
		return plans.findAllByOrderBySortOrderAscIdAsc().stream().map(this::toResponse).toList();
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	@Transactional
	public PlanResponse create(@Valid @RequestBody PlanRequest request) {
		if (plans.existsByCodeIgnoreCase(request.code())) {
			throw new ConflictException("Já existe um plano com o código \"" + request.code() + "\"");
		}
		Plan plan = new Plan(request.code(), request.name().trim(), blankToNull(request.description()), request.priceCents(),
				request.maxDevices(), request.maxStorageMb(), request.maxUsers());
		apply(plan, request);
		return toResponse(plans.save(plan));
	}

	/** O código não muda (é como o plano é referenciado); o resto vale para os clientes que o usam já na próxima requisição. */
	@PutMapping("/{id}")
	@Transactional
	public PlanResponse update(@PathVariable Long id, @Valid @RequestBody PlanRequest request) {
		Plan plan = find(id);
		if (!plan.getCode().equals(request.code())) {
			throw new ConflictException("O código de um plano não pode ser alterado");
		}
		plan.setName(request.name().trim());
		plan.setDescription(blankToNull(request.description()));
		plan.setPriceCents(request.priceCents());
		plan.setMaxDevices(request.maxDevices());
		plan.setMaxStorageMb(request.maxStorageMb());
		plan.setMaxUsers(request.maxUsers());
		apply(plan, request);
		return toResponse(plans.save(plan));
	}

	@DeleteMapping("/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	@Transactional
	public void delete(@PathVariable Long id) {
		Plan plan = find(id);
		long using = tenants.countByPlanId(id);
		if (using > 0) {
			throw new ConflictException("O plano \"" + plan.getName() + "\" é usado por " + using + " cliente(s). Troque o plano deles ou desative este plano em vez de excluir.");
		}
		plans.delete(plan);
	}

	private void apply(Plan plan, PlanRequest request) {
		plan.setMaxUploadMb(request.maxUploadMb());
		if (request.features() != null) {
			plan.setFeatures(request.features());
		}
		if (request.active() != null) {
			plan.setActive(request.active());
		}
		if (request.sortOrder() != null) {
			plan.setSortOrder(request.sortOrder());
		}
	}

	private Plan find(Long id) {
		return plans.findById(id).orElseThrow(() -> new NoSuchElementException("Plano não encontrado: " + id));
	}

	private PlanResponse toResponse(Plan plan) {
		return new PlanResponse(plan.getId(), plan.getCode(), plan.getName(), plan.getDescription(), plan.getPriceCents(), plan.getCurrency(),
				plan.getMaxDevices(), plan.getMaxStorageMb(), plan.getMaxUsers(), plan.getMaxUploadMb(),
				plan.getFeatures().stream().sorted().map(Feature::name).toList(), plan.isActive(), plan.getSortOrder(), tenants.countByPlanId(plan.getId()));
	}

	private static String blankToNull(String text) {
		return text == null || text.isBlank() ? null : text.trim();
	}

}
