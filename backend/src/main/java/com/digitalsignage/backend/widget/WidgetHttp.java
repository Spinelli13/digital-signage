package com.digitalsignage.backend.widget;

import java.io.IOException;
import java.net.URI;

/**
 * Como o servidor busca dados de fora (clima, cotações, feeds). Fica atrás de uma interface para os testes responderem
 * sem rede e para a regra de segurança (só endereços públicos, limite de tamanho e de tempo) ficar num lugar só.
 */
public interface WidgetHttp {

	record Response(int status, byte[] body, String contentType) {
	}

	/**
	 * GET com limite de tamanho e de tempo. {@code publicOnly}: o endereço veio de uma pessoa (feed personalizado), então cada
	 * destino (inclusive os de redirecionamentos) precisa ser público.
	 */
	Response get(URI uri, boolean publicOnly) throws IOException;

}
