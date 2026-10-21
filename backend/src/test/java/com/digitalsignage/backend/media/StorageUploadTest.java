package com.digitalsignage.backend.media;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;

import com.digitalsignage.backend.common.ApiExceptionHandler;

import software.amazon.awssdk.awscore.exception.AwsErrorDetails;
import software.amazon.awssdk.services.s3.model.S3Exception;

/** O envio de arquivos ao armazenamento: o tipo que vai e a explicação quando ele recusa. */
class StorageUploadTest {

	private static MockMultipartFile file(String name, String type) {
		return new MockMultipartFile("file", name, type, new byte[] { 1, 2, 3 });
	}

	private static S3Exception s3(int status, String message) {
		return (S3Exception) S3Exception.builder().statusCode(status).message(message)
				.awsErrorDetails(AwsErrorDetails.builder().errorMessage(message).build()).build();
	}

	@Test
	void theTypeSentToTheStorageIsTheCanonicalOne() {
		assertEquals("video/quicktime", S3MediaStorage.contentTypeOf(file("IMG_0001.MOV", "video/quicktime")));
		assertEquals("video/quicktime", S3MediaStorage.contentTypeOf(file("IMG_0001.MOV", "application/octet-stream")));
		assertEquals("video/quicktime", S3MediaStorage.contentTypeOf(file("IMG_0001.MOV", "")));
		assertEquals("video/mp4", S3MediaStorage.contentTypeOf(file("clipe.m4v", "video/x-m4v")));
		assertEquals("image/png", S3MediaStorage.contentTypeOf(file("logo.png", "application/octet-stream")));
		assertEquals("image/jpeg", S3MediaStorage.contentTypeOf(file("foto.jpg", "image/jpeg; charset=binary")));
	}

	@Test
	void anUnknownTypeIsLeftAsTheBrowserSentIt() {
		assertEquals("text/plain", S3MediaStorage.contentTypeOf(file("a.txt", "text/plain")));
	}

	@Test
	void aTypeTheBucketRefusesBecomesAnExplanation() {
		ResponseEntity<java.util.Map<String, String>> response = new ApiExceptionHandler()
				.handleStorage(s3(415, "mime type video/quicktime is not supported"));

		assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
		assertTrue(response.getBody().get("error").contains("não aceita este tipo de arquivo (mime type video/quicktime is not supported)"));
	}

	@Test
	void aFileOverTheLimitSaysSo() {
		ResponseEntity<java.util.Map<String, String>> response = new ApiExceptionHandler().handleStorage(s3(413, "The object exceeded the maximum allowed size"));

		assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
		assertTrue(response.getBody().get("error").contains("passa do limite do armazenamento (50 MB"));
	}

	@Test
	void otherClientErrorsAreExplainedButCredentialProblemsStayInternalErrors() {
		assertEquals(HttpStatus.BAD_REQUEST, new ApiExceptionHandler().handleStorage(s3(400, "bucket not found")).getStatusCode());
		assertThrows(S3Exception.class, () -> new ApiExceptionHandler().handleStorage(s3(403, "forbidden")));
		assertThrows(S3Exception.class, () -> new ApiExceptionHandler().handleStorage(s3(503, "slow down")));
	}

}
