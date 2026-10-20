package com.digitalsignage.backend.monitoring;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * CSV para o Excel em português: separador ";" (o que ele espera na configuração regional do Brasil) e BOM UTF-8
 * (para os acentos abrirem certos). Células que começariam com = + - @ recebem um apóstrofo na frente, para o
 * Excel não executá-las como fórmula (injeção de CSV).
 */
public final class CsvWriter {

	private static final String BOM = "﻿";

	private final StringBuilder out = new StringBuilder(BOM);

	public CsvWriter row(Object... cells) {
		for (int i = 0; i < cells.length; i++) {
			if (i > 0) {
				out.append(';');
			}
			out.append(cell(cells[i]));
		}
		out.append("\r\n");
		return this;
	}

	public CsvWriter rows(List<List<Object>> rows) {
		rows.forEach(row -> row(row.toArray()));
		return this;
	}

	public byte[] bytes() {
		return out.toString().getBytes(StandardCharsets.UTF_8);
	}

	static String cell(Object value) {
		if (value == null) {
			return "";
		}
		String text = value.toString();
		if (!text.isEmpty() && "=+-@\t\r".indexOf(text.charAt(0)) >= 0) {
			text = "'" + text;
		}
		if (text.contains(";") || text.contains("\"") || text.contains("\n") || text.contains("\r")) {
			return "\"" + text.replace("\"", "\"\"") + "\"";
		}
		return text;
	}

}
