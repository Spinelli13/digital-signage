package com.digitalsignage.backend;

import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

class MediaLibraryTest extends AbstractApiTest {

	@Test
	void uploadStoresChecksumComputedOnTheServer() throws Exception {
		byte[] content = "conteudo-da-imagem".getBytes(StandardCharsets.UTF_8);
		String expected = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));

		long id = uploadMedia("logo.png", "image/png", content);

		mvc.perform(asAdmin(get("/api/v1/media/" + id)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.checksum").value(expected))
				.andExpect(jsonPath("$.type").value("IMAGE"))
				.andExpect(jsonPath("$.fileSize").value(content.length));
	}

	@Test
	void videoUploadKeepsOptionalDuration() throws Exception {
		MockMultipartFile file = new MockMultipartFile("file", "clip.mp4", "video/mp4", new byte[] { 1, 2, 3 });

		mvc.perform(asAdmin(multipart("/api/v1/media").file(file).param("durationSeconds", "42")))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.type").value("VIDEO"))
				.andExpect(jsonPath("$.durationSeconds").value(42));
	}

	@Test
	void unsupportedFileTypeIsRejected() throws Exception {
		MockMultipartFile file = new MockMultipartFile("file", "doc.pdf", "application/pdf", new byte[] { 1 });

		mvc.perform(asAdmin(multipart("/api/v1/media").file(file))).andExpect(status().isBadRequest());
		mvc.perform(asAdmin(get("/api/v1/media"))).andExpect(jsonPath("$", hasSize(0)));
	}

	@Test
	void emptyFileIsRejected() throws Exception {
		MockMultipartFile file = new MockMultipartFile("file", "vazio.png", "image/png", new byte[0]);

		mvc.perform(asAdmin(multipart("/api/v1/media").file(file))).andExpect(status().isBadRequest());
	}

	@Test
	void downloadReturnsExactlyTheUploadedBytes() throws Exception {
		byte[] content = new byte[4096];
		for (int i = 0; i < content.length; i++) {
			content[i] = (byte) (i * 31);
		}
		long id = uploadMedia("banner.png", "image/png", content);

		byte[] downloaded = mvc.perform(asAdmin(get("/api/v1/media/" + id + "/file")))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsByteArray();

		assertArrayEquals(content, downloaded);
	}

	@Test
	void deleteRemovesRecordAndFile() throws Exception {
		long id = uploadPng("apagar");

		mvc.perform(asAdmin(delete("/api/v1/media/" + id))).andExpect(status().isNoContent());

		mvc.perform(asAdmin(get("/api/v1/media/" + id))).andExpect(status().isNotFound());
		mvc.perform(asAdmin(get("/api/v1/media/" + id + "/file"))).andExpect(status().isNotFound());
		assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM media", Integer.class));
	}

	@Test
	void urlEndpointPointsAtTheAuthenticatedFileEndpointForLocalStorage() throws Exception {
		long id = uploadPng("local");

		mvc.perform(asAdmin(get("/api/v1/media/" + id + "/url")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.url").value("/api/v1/media/" + id + "/file"))
				.andExpect(jsonPath("$.requiresAuth").value(true));
	}

	@Test
	void unknownMediaIsNotFound() throws Exception {
		mvc.perform(asAdmin(get("/api/v1/media/999999"))).andExpect(status().isNotFound());
		mvc.perform(asAdmin(delete("/api/v1/media/999999"))).andExpect(status().isNotFound());
	}

}
