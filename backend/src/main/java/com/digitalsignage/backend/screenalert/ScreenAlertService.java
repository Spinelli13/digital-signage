package com.digitalsignage.backend.screenalert;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.NoSuchElementException;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.digitalsignage.backend.common.ConflictException;
import com.digitalsignage.backend.device.Device;
import com.digitalsignage.backend.device.DeviceRepository;
import com.digitalsignage.backend.group.DeviceGroupRepository;
import com.digitalsignage.backend.screenalert.ScreenAlert.Target;
import com.digitalsignage.backend.screenalert.ScreenAlert.Tone;

/**
 * "Alertar agora": cria, encerra e entrega à TV o aviso em tela cheia. Todo aviso tem prazo (padrão 15 minutos, no máximo 24 horas),
 * e há um teto de avisos no ar ao mesmo tempo para um engano não cobrir tudo com uma pilha deles.
 */
@Service
public class ScreenAlertService {

	static final int MAX_MESSAGE = 300;
	static final int MAX_TITLE = 60;
	static final int DEFAULT_MINUTES = 15;
	static final int MAX_MINUTES = 24 * 60;
	static final int MAX_ACTIVE = 5;

	/** O que a TV recebe no heartbeat: o texto e quanto falta para acabar (a TV conta o tempo sozinha, sem depender do relógio dela). */
	public record PlayerAlert(Long id, String title, String message, String tone, long remainingSeconds) {
	}

	private final ScreenAlertRepository alerts;
	private final DeviceRepository devices;
	private final DeviceGroupRepository groups;

	public ScreenAlertService(ScreenAlertRepository alerts, DeviceRepository devices, DeviceGroupRepository groups) {
		this.alerts = alerts;
		this.devices = devices;
		this.groups = groups;
	}

	@Transactional
	public ScreenAlert create(String title, String message, Tone tone, Target target, Long targetId, Integer durationMinutes, String by, Instant now) {
		String text = message == null ? "" : message.strip();
		if (text.isEmpty()) {
			throw new IllegalArgumentException("Escreva a mensagem do aviso");
		}
		if (text.length() > MAX_MESSAGE) {
			throw new IllegalArgumentException("A mensagem do aviso pode ter no máximo " + MAX_MESSAGE + " caracteres");
		}
		String heading = title == null || title.isBlank() ? null : title.strip();
		if (heading != null && heading.length() > MAX_TITLE) {
			throw new IllegalArgumentException("O título do aviso pode ter no máximo " + MAX_TITLE + " caracteres");
		}
		int minutes = durationMinutes == null ? DEFAULT_MINUTES : durationMinutes;
		if (minutes < 1 || minutes > MAX_MINUTES) {
			throw new IllegalArgumentException("A duração do aviso vai de 1 minuto a 24 horas");
		}
		Long scope = switch (target) {
			case ALL -> null;
			case GROUP -> {
				if (targetId == null || groups.findById(targetId).isEmpty()) {
					throw new NoSuchElementException("Grupo não encontrado");
				}
				yield targetId;
			}
			case DEVICE -> {
				if (targetId == null || devices.findById(targetId).isEmpty()) {
					throw new NoSuchElementException("Dispositivo não encontrado");
				}
				yield targetId;
			}
		};
		if (alerts.findActive(now).size() >= MAX_ACTIVE) {
			throw new ConflictException("Já há " + MAX_ACTIVE + " avisos no ar. Encerre algum antes de mandar outro");
		}
		return alerts.save(new ScreenAlert(heading, text, tone, target, scope, by, now, now.plus(Duration.ofMinutes(minutes))));
	}

	/** Encerra o aviso agora. Encerrar o que já acabou não é erro (o botão pode ser clicado duas vezes). */
	@Transactional
	public ScreenAlert end(Long id, String by, Instant now) {
		ScreenAlert alert = alerts.findById(id).orElseThrow(() -> new NoSuchElementException("Aviso não encontrado: " + id));
		alert.end(now, by);
		return alerts.save(alert);
	}

	@Transactional(readOnly = true)
	public List<ScreenAlert> active(Instant now) {
		return alerts.findActive(now);
	}

	@Transactional(readOnly = true)
	public List<ScreenAlert> recent() {
		return alerts.findTop30ByOrderByCreatedAtDescIdDesc();
	}

	/** O aviso que esta TV deve mostrar agora (o mais urgente; entre iguais, o mais novo), ou nulo se não há nenhum. */
	@Transactional(readOnly = true)
	public PlayerAlert forDevice(Device device, Instant now) {
		return alerts.findActive(now).stream()
				.filter(alert -> alert.appliesTo(device))
				.max(Comparator.comparingInt((ScreenAlert alert) -> -alert.getTone().ordinal()).thenComparing(ScreenAlert::getCreatedAt))
				.map(alert -> new PlayerAlert(alert.getId(), alert.getTitle(), alert.getMessage(), alert.getTone().name(),
						Math.max(1, Duration.between(now, alert.getExpiresAt()).getSeconds())))
				.orElse(null);
	}
}
