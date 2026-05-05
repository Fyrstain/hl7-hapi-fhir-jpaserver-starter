package ca.uhn.fhir.jpa.starter.cohort.r5;

import ca.uhn.fhir.jpa.starter.cohort.service.r5.CryptoUtils;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CryptoUtilsTest {

	@Test
	void TestEncryptAndDecryptPatientId() throws Exception {
		String originalId = "patient-12345";

		String encrypted = CryptoUtils.encrypt(originalId);
		assertNotNull(encrypted);
		assertNotEquals(originalId, encrypted);

		String decrypted = CryptoUtils.decrypt(encrypted);
		assertEquals(originalId, decrypted);
	}

	@Test
	void TestEncryptWithSameInput() throws Exception {
		String id = "same-input";

		String encrypted1 = CryptoUtils.encrypt(id);
		String encrypted2 = CryptoUtils.encrypt(id);

		assertEquals(encrypted1, encrypted2); // AES with same key, same input = deterministic output
	}

	@Test
	void TestDecryptWithInvalidHex() {
		String invalidHex = "this-is-not-hex";

		assertThrows(Exception.class, () -> {
			CryptoUtils.decrypt(invalidHex);
		});
	}

	@Test
	void TestEncryptDecryptWithEmptyString() throws Exception {
		String empty = "";

		String encrypted = CryptoUtils.encrypt(empty);
		String decrypted = CryptoUtils.decrypt(encrypted);

		assertEquals(empty, decrypted);
	}
}
