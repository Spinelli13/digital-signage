package com.digitalsignage.backend.template;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.digitalsignage.backend.widget.WidgetKind;

/**
 * Modelos prontos por segmento (barbearia, restaurante, clínica, academia, loja). Cada um é só dados: os textos que a pessoa
 * confere ("campos"), os avisos que rodam na tela principal, os da faixa lateral, e (quando faz sentido) uma tabela de preços. O
 * {@link SegmentTemplateService} transforma isto em widgets, playlists, tabela e layout do cliente; depois tudo é editável como
 * qualquer coisa criada à mão.
 *
 * <p>Nos textos, {@code {campo}} vira o que a pessoa digitou (ou o padrão do campo).
 */
public final class SegmentTemplates {

	/** Um texto que a pessoa confere antes de criar (nome do negócio, promoção, horário...). */
	public record Field(String key, String label, String defaultValue, int maxLength) {
	}

	/** Um aviso (widget NOTICE) do modelo. {@code accent}: brand (megafone), amber ou red (triângulo de alerta: só para avisos que pedem atenção). */
	public record Notice(String key, String title, String message, String accent, int seconds) {
	}

	/** Um item da tabela de preços do modelo (o cliente ajusta os valores depois). */
	public record PriceDef(String category, String name, String price, String unit) {
		public BigDecimal value() {
			return new BigDecimal(price);
		}
	}

	public record Template(
			String id,
			String name,
			/** O ícone do painel (nome do conjunto de ícones). */
			String icon,
			String description,
			List<Field> fields,
			/** Os avisos da tela principal, na ordem; a tabela de preços (se houver) entra depois deles. */
			List<Notice> main,
			/** Os avisos da faixa lateral. */
			List<Notice> side,
			/** A tabela de preços (vazia = o modelo não tem). */
			String priceTitle,
			List<PriceDef> prices) {

		public boolean hasPrices() {
			return !prices.isEmpty();
		}
	}

	private static Field field(String key, String label, String defaultValue) {
		return new Field(key, label, defaultValue, key.equals("name") ? 60 : 120);
	}

	private static Notice notice(String key, String title, String message, String accent, int seconds) {
		return new Notice(key, title, message, accent, seconds);
	}

	private static PriceDef price(String category, String name, String price) {
		return new PriceDef(category, name, price, null);
	}

	public static final List<Template> ALL = List.of(
			new Template("barbearia", "Barbearia", "scissors",
					"Boas-vindas, promoção da semana, tabela de serviços, horário e como agendar, com relógio e previsão do tempo na lateral.",
					List.of(field("name", "Nome da barbearia", "Barbearia"),
							field("promo", "Promoção da semana", "Terça: corte + barba por R$ 49"),
							field("hours", "Horário de atendimento", "Seg a Sex 9h às 20h · Sáb 9h às 18h"),
							field("contact", "Como agendar", "Agende pelo WhatsApp")),
					List.of(notice("welcome", "{name}", "Bem-vindo! {contact}", "brand", 10),
							notice("promo", "Promoção da semana", "{promo}", "brand", 12)),
					List.of(notice("hours", "Horário", "{hours}", "brand", 10),
							notice("contact", "Agende", "{contact}", "brand", 10)),
					"Nossos serviços",
					List.of(price("Cabelo", "Corte de cabelo", "45"), price("Barba", "Barba", "35"), price("Combo", "Corte + barba", "70"),
							price("Cabelo", "Sobrancelha", "15"), price("Cabelo", "Pigmentação", "40"), price("Cabelo", "Hidratação", "30"))),
			new Template("restaurante", "Restaurante", "utensils",
					"Boas-vindas, prato do dia, cardápio, horário e como reservar, com relógio e previsão do tempo na lateral.",
					List.of(field("name", "Nome do restaurante", "Restaurante"),
							field("special", "Prato do dia", "Feijoada completa por R$ 39,90"),
							field("hours", "Horário de funcionamento", "Seg a Sáb 11h às 15h e 18h às 23h"),
							field("contact", "Pedidos e reservas", "Reservas pelo WhatsApp")),
					List.of(notice("welcome", "{name}", "Seja bem-vindo! {contact}", "brand", 10),
							notice("special", "Prato do dia", "{special}", "brand", 12)),
					List.of(notice("hours", "Horário", "{hours}", "brand", 10),
							notice("contact", "Reservas", "{contact}", "brand", 10)),
					"Cardápio",
					List.of(price("Pratos", "Prato executivo", "32"), price("Pratos", "Feijoada completa", "39.90"), price("Pratos", "Salada", "24"),
							price("Bebidas", "Suco natural", "9"), price("Bebidas", "Refrigerante", "7"), price("Sobremesas", "Sobremesa do dia", "14"))),
			new Template("clinica", "Clínica", "stethoscope",
					"Boas-vindas, aviso aos pacientes, horário e como agendar, com relógio e previsão do tempo na lateral.",
					List.of(field("name", "Nome da clínica", "Clínica"),
							field("notice", "Aviso aos pacientes", "Chegue 10 minutos antes da consulta"),
							field("hours", "Horário de atendimento", "Seg a Sex 8h às 18h"),
							field("contact", "Agendamentos", "Agende pelo telefone ou WhatsApp")),
					List.of(notice("welcome", "{name}", "Seja bem-vindo! Estamos felizes em cuidar de você.", "brand", 10),
							notice("notice", "Aviso aos pacientes", "{notice}", "amber", 12),
							notice("contact", "Agendamentos", "{contact}", "brand", 10)),
					List.of(notice("hours", "Horário", "{hours}", "brand", 10)),
					"", List.of()),
			new Template("academia", "Academia", "dumbbell",
					"Boas-vindas, promoção de matrícula, planos, horário e contato, com relógio e previsão do tempo na lateral.",
					List.of(field("name", "Nome da academia", "Academia"),
							field("promo", "Promoção do mês", "Matrícula grátis neste mês"),
							field("hours", "Horário de funcionamento", "Seg a Sex 6h às 22h · Sáb 8h às 14h"),
							field("contact", "Contato", "Venha conhecer: fale com a recepção")),
					List.of(notice("welcome", "{name}", "Bora treinar! {contact}", "brand", 10),
							notice("promo", "Promoção do mês", "{promo}", "brand", 12)),
					List.of(notice("hours", "Horário", "{hours}", "brand", 10),
							notice("contact", "Contato", "{contact}", "brand", 10)),
					"Nossos planos",
					List.of(price("Planos", "Mensal", "99"), price("Planos", "Trimestral", "270"), price("Planos", "Semestral", "480"),
							price("Planos", "Anual", "840"))),
			new Template("loja", "Loja", "shopping-bag",
					"Boas-vindas, promoção, horário e contato, com relógio e previsão do tempo na lateral.",
					List.of(field("name", "Nome da loja", "Loja"),
							field("promo", "Promoção", "Liquidação: até 50% de desconto"),
							field("hours", "Horário de funcionamento", "Seg a Sáb 9h às 19h"),
							field("contact", "Contato", "Siga a gente no Instagram")),
					List.of(notice("welcome", "{name}", "Seja bem-vindo! {contact}", "brand", 10),
							notice("promo", "Promoção", "{promo}", "brand", 12)),
					List.of(notice("hours", "Horário", "{hours}", "brand", 10),
							notice("contact", "Contato", "{contact}", "brand", 10)),
					"", List.of()));

	private SegmentTemplates() {
	}

	public static Optional<Template> find(String id) {
		return ALL.stream().filter(template -> template.id().equals(id)).findFirst();
	}

	/** Os textos que valem para a criação: o que a pessoa digitou (limpo) ou o padrão de cada campo. */
	public static Map<String, String> resolve(Template template, Map<String, String> typed) {
		Map<String, String> values = new LinkedHashMap<>();
		for (Field field : template.fields()) {
			String value = typed == null ? null : typed.get(field.key());
			String clean = value == null ? "" : value.replaceAll("[\\p{Cntrl}&&[^\\n]]", " ").strip();
			if (clean.length() > field.maxLength()) {
				throw new IllegalArgumentException("O campo \"" + field.label() + "\" aceita no máximo " + field.maxLength() + " caracteres");
			}
			values.put(field.key(), clean.isEmpty() ? field.defaultValue() : clean);
		}
		return values;
	}

	/** Troca {campo} pelo valor. */
	public static String fill(String text, Map<String, String> values) {
		String out = text;
		for (Map.Entry<String, String> entry : values.entrySet()) {
			out = out.replace("{" + entry.getKey() + "}", entry.getValue());
		}
		return out;
	}

	/** O tipo de widget usado pelos avisos (só para o serviço não repetir a constante). */
	static WidgetKind noticeKind() {
		return WidgetKind.NOTICE;
	}
}
