package ca.uhn.fhir.jpa.starter.mapping.model;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class HL7v2BuilderTest {

	@Test
	void putByPath_shouldCreateSimpleField() {
		HL7v2Builder builder = new HL7v2Builder();

		builder.putByPath("MSH-10", "MSGID");

		String result = builder.build();
		assertEquals("MSH||||||||||MSGID\r", result);
	}

	@Test
	void putByPath_shouldCreateComponent() {
		HL7v2Builder builder = new HL7v2Builder();

		builder.putByPath("PID-5-1", "DOE");
		builder.putByPath("PID-5-2", "JOHN");

		String result = builder.build();
		assertEquals("PID|||||DOE^JOHN\r", result);
	}

	@Test
	void putByPath_shouldCreateSubComponent() {
		HL7v2Builder builder = new HL7v2Builder();

		builder.putByPath("OBX-5-1-1", "ABC");
		builder.putByPath("OBX-5-1-2", "DEF");

		String result = builder.build();
		assertEquals("OBX|||||ABC&DEF\r", result);
	}

	@Test
	void putByPath_shouldAppendRepetitionWithPlusToken() {
		HL7v2Builder builder = new HL7v2Builder();

		builder.putByPath("PID-3[+]-1", "ID1");
		builder.putByPath("PID-3[+]-1", "ID2");

		String result = builder.build();
		assertEquals("PID|||ID1~ID2\r", result);
	}

	@Test
	void putByPath_shouldAppendComponentWithPlusToken() {
		HL7v2Builder builder = new HL7v2Builder();

		builder.putByPath("PID-5-+", "DOE");
		builder.putByPath("PID-5-+", "JOHN");

		String result = builder.build();
		assertEquals("PID|||||DOE^JOHN\r", result);
	}

	@Test
	void addSegment_shouldAddCompleteSegment() {
		HL7v2Builder builder = new HL7v2Builder();

		builder.addSegment(
			"OBR",
			List.of(
				new String[]{"0001"},
				new String[]{"5094108743", "4108743"},
				new String[]{"NF", "NFP", "L"}
			)
		);

		String result = builder.build();
		assertEquals("OBR|0001|5094108743^4108743|NF^NFP^L\r", result);
	}

	@Test
	void putByPath_shouldHandleMultipleSegments() {
		HL7v2Builder builder = new HL7v2Builder();

		builder.putByPath("PID-3-1", "ID1");
		builder.putByPath("PID[+]-3-1", "ID2");

		String result = builder.build();
		assertEquals(
			"PID|||ID1\r" +
				"PID|||ID2\r",
			result
		);
	}

	@Test
	void putByPath_shouldIgnoreValueSuffix() {
		HL7v2Builder builder = new HL7v2Builder();

		builder.putByPath("PID-2.value", "PATIENTID");

		String result = builder.build();
		assertEquals("PID||PATIENTID\r", result);
	}

	@Test
	void putByPath_invalidPath_shouldThrowException() {
		HL7v2Builder builder = new HL7v2Builder();

		IllegalArgumentException ex = assertThrows(
			IllegalArgumentException.class,
			() -> builder.putByPath("INVALIDPATH", "X")
		);

		assertTrue(ex.getMessage().contains("Invalid HL7v2 path"));
	}

	@Test
	void putByPath_shouldReuseLastSegmentWithEqualsToken() {
		HL7v2Builder builder = new HL7v2Builder();

		builder.putByPath("OBR[+]-4-1", "1988-5");
		builder.putByPath("OBR[=]-4-2", "Protéine C réactive");
		builder.putByPath("OBR[=]-4-3", "http://loinc.org");

		String result = builder.build();
		assertEquals("OBR||||1988-5^Protéine C réactive^http://loinc.org\r", result);
	}

	@Test
	void putByPath_shouldThrowWhenEqualsTokenUsedWithoutPreviousSegment() {
		HL7v2Builder builder = new HL7v2Builder();

		IllegalStateException ex = assertThrows(
			IllegalStateException.class,
			() -> builder.putByPath("OBX[=]-3-1", "1988-5")
		);

		assertTrue(ex.getMessage().contains("No previous segment for token [=]"));
	}

	@Test
	void putByPath_shouldAddObrThenObxInGlobalInsertionOrder() {
		HL7v2Builder builder = new HL7v2Builder();

		builder.putByPath("OBR[+]-4-1", "1988-5");
		builder.putByPath("OBR[=]-4-2", "Protéine C réactive");
		builder.putByPath("OBX[+]-3-1", "1988-5");
		builder.putByPath("OBX[=]-3-2", "Protéine C réactive");
		builder.putByPath("OBX[=]-5", "< 3.0");

		String result = builder.build();
		assertEquals(
			"OBR||||1988-5^Protéine C réactive\r" +
				"OBX|||1988-5^Protéine C réactive||< 3.0\r",
			result
		);
	}

	@Test
	void putByPath_shouldPreserveOrderAcrossMultipleObrObxBlocks() {
		HL7v2Builder builder = new HL7v2Builder();

		builder.putByPath("OBR[+]-4-1", "1988-5");
		builder.putByPath("OBX[+]-3-1", "1988-5");
		builder.putByPath("OBX[=]-5", "< 3.0");

		builder.putByPath("OBR[+]-4-1", "2951-2");
		builder.putByPath("OBX[+]-3-1", "2951-2");
		builder.putByPath("OBX[=]-5", "142");

		String result = builder.build();
		assertEquals(
			"OBR||||1988-5\r" +
				"OBX|||1988-5||< 3.0\r" +
				"OBR||||2951-2\r" +
				"OBX|||2951-2||142\r",
			result
		);
	}

	@Test
	void putByPath_shouldReuseLastObxWithEqualsTokenAfterAnotherSegmentTypeWasAdded() {
		HL7v2Builder builder = new HL7v2Builder();

		builder.putByPath("OBX[+]-3-1", "2951-2");
		builder.putByPath("OBR[+]-4-1", "1988-5");
		builder.putByPath("OBX[=]-5", "142");

		String result = builder.build();
		assertEquals(
			"OBX|||2951-2||142\r" +
				"OBR||||1988-5\r",
			result
		);
	}

	@Test
	void putByPath_shouldAllowMultipleFieldsOnSameSegmentUsingEqualsToken() {
		HL7v2Builder builder = new HL7v2Builder();

		builder.putByPath("OBX[+]-2", "NM");
		builder.putByPath("OBX[=]-3-1", "2951-2");
		builder.putByPath("OBX[=]-3-2", "Sodium");
		builder.putByPath("OBX[=]-5", "142");
		builder.putByPath("OBX[=]-6", "mmol/l");

		String result = builder.build();
		assertEquals("OBX||NM|2951-2^Sodium||142|mmol/l\r", result);
	}

	@Test
	void putByPath_shouldHandleEqualsTokenForRepetitions() {
		HL7v2Builder builder = new HL7v2Builder();

		builder.putByPath("OBX[+]-8[+]-1", "N");
		builder.putByPath("OBX[=]-8[+]-1", "H");
		builder.putByPath("OBX[=]-8[=]-2", "High");

		String result = builder.build();
		assertEquals("OBX||||||||N~H^High\r", result);
	}

	@Test
	void getSegmentOrder_shouldReflectRealInsertionOrder() {
		HL7v2Builder builder = new HL7v2Builder();

		builder.putByPath("MSH-10", "MSG1");
		builder.putByPath("PID-3-1", "PAT1");
		builder.putByPath("OBR[+]-4-1", "1988-5");
		builder.putByPath("OBX[+]-3-1", "1988-5");
		builder.putByPath("OBR[+]-4-1", "2951-2");
		builder.putByPath("OBX[+]-3-1", "2951-2");

		assertEquals(
			List.of("MSH", "PID", "OBR", "OBX", "OBR", "OBX"),
			builder.getSegmentOrder()
		);
	}
}