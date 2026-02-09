package ca.uhn.fhir.jpa.starter.cohort.service.r5;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigInteger;

public class CryptoUtils {

	private static final String ENCRYPTION_KEY = "1234567890123456";
	private static final String ALGORITHM = "AES";

	public static String encrypt(String realId) throws Exception {
		SecretKeySpec key = new SecretKeySpec(ENCRYPTION_KEY.getBytes(), ALGORITHM);
		Cipher cipher = Cipher.getInstance(ALGORITHM);
		cipher.init(Cipher.ENCRYPT_MODE, key);
		byte[] encryptedBytes = cipher.doFinal(realId.getBytes());
		return new BigInteger(1, encryptedBytes).toString(16);
	}

	public static String decrypt(String encryptedId) throws Exception {
		SecretKeySpec key = new SecretKeySpec(ENCRYPTION_KEY.getBytes(), ALGORITHM);
		Cipher cipher = Cipher.getInstance(ALGORITHM);
		cipher.init(Cipher.DECRYPT_MODE, key);
		byte[] decodedBytes = new BigInteger(encryptedId, 16).toByteArray();

		if (decodedBytes[0] == 0) {
			byte[] tmp = new byte[decodedBytes.length - 1];
			System.arraycopy(decodedBytes, 1, tmp, 0, tmp.length);
			decodedBytes = tmp;
		}

		byte[] decryptedBytes = cipher.doFinal(decodedBytes);
		return new String(decryptedBytes);
	}
}
