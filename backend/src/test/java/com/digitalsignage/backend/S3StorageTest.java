package com.digitalsignage.backend;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

/**
 * Com STORAGE_TYPE=s3 o backend não serve o arquivo: responde 302 para uma URL assinada.
 * A assinatura é calculada offline, então não precisa de um S3 de verdade (o upload real só é
 * exercitado no deploy — ver docs/DEPLOY.md).
 */
@TestPropertySource(properties = {
		"app.storage.type=s3",
		"app.storage.s3.bucket=signage-media",
		"app.storage.s3.endpoint=https://s3.example.test",
		"app.storage.s3.region=auto",
		"app.storage.s3.access-key=test-access-key",
		"app.storage.s3.secret-key=test-secret-key"
})
class S3StorageTest extends AbstractApiTest {

	private long insertMediaRow(String storageKey) {
		return jdbc.queryForObject(
				"INSERT INTO media (name, type, mime_type, storage_key, file_size, checksum) "
						+ "VALUES ('foto', 'IMAGE', 'image/png', ?, 10, 'abc') RETURNING id",
				Long.class, storageKey);
	}

	@Test
	void fileEndpointRedirectsToASignedUrl() throws Exception {
		long id = insertMediaRow("chave-123.png");

		mvc.perform(asAdmin(get("/api/v1/media/" + id + "/file")))
				.andExpect(status().isFound())
				.andExpect(header().string("Location", startsWith("https://s3.example.test/signage-media/chave-123.png?")))
				.andExpect(header().string("Location", containsString("X-Amz-Signature=")))
				.andExpect(header().string("Location", containsString("response-content-type=image%2Fpng")));
	}

	@Test
	void urlEndpointExposesTheSignedUrlWithoutRequiringAuth() throws Exception {
		long id = insertMediaRow("chave-456.png");

		mvc.perform(asAdmin(get("/api/v1/media/" + id + "/url")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.requiresAuth").value(false))
				.andExpect(jsonPath("$.url", containsString("chave-456.png")));
	}

}
