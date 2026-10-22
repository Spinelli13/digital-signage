package com.digitalsignage.backend.widget;

import java.io.IOException;
import java.net.URI;

/**
 * Como o servidor busca dados de fora (clima, cotações, feeds). Fica atrás de uma interface para os testes responderem
 * sem rede e para a regra de segurança (só endereços públicos, limite de tamanho e de tempo) ficar num lugar só.
 */
public interface WidgetHttp {

	/** {@code lastModified} e {@code expires} são os cabeçalhos HTTP da resposta (nulos quando o provedor não os manda). */
	record Response(int status, byte[] body, String contentType, String lastModified, String expires) {

		public Response(int status, byte[] body, String contentType) {
			this(status, body, contentType, null, null);
		}
	}

	/**
	 * GET com limite de tamanho e de tempo. {@code publicOnly}: o endereço veio de uma pessoa (feed personalizado), então cada
	 * destino (inclusive os de redirecionamentos) precisa ser público.
	 */
	Response get(URI uri, boolean publicOnly) throws IOException;

	/**
	 * GET condicional: com {@code ifModifiedSince} (o {@code Last-Modified} da resposta anterior) o provedor responde 304, sem
	 * corpo, quando nada mudou. É o que o MET Norway pede de quem consulta a previsão com frequência.
	 */
	default Response get(URI uri, boolean publicOnly, String ifModifiedSince) throws IOException {
		return get(uri, publicOnly);
	}

}
