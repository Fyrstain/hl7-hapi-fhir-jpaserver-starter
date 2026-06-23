package ca.uhn.fhir.jpa.starter.mapping.service;

import ca.uhn.fhir.jpa.starter.mapping.model.HPRIMMessage;
import ca.uhn.fhir.jpa.starter.mapping.model.HPRIMSegment;
import ca.uhn.fhir.rest.server.exceptions.InternalErrorException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

public class HPRIMDataReader {

	private static final Logger logger = LoggerFactory.getLogger(HPRIMDataReader.class);

	private static final Map<String, Map<String, Integer>> LEVELS_BY_MESSAGE_TYPE = Map.of(
			"ORM", levels("H", 1, "P", 2, "OBR", 3, "OBX", 4, "L", 1),
			"ORA", levels("H", 1, "P", 2, "AP", 3, "AC", 4, "OBR", 5, "OBX", 6, "L", 1),
			"ORU", levels("H", 1, "P", 2, "OBR", 3, "OBX", 4, "L", 1),
			"ADM", levels("H", 1, "P", 2, "AP", 3, "AC", 4, "L", 1),
			"FAC", levels("H", 1, "P", 2, "AP", 3, "AC", 4, "FAC", 5, "REG", 6, "ACT", 7, "L", 1),
			"REG", levels("H", 1, "P", 2, "REG", 3, "L", 1),
			"ERR", levels("H", 1, "ERR", 2, "L", 1));

	private static final Map<String, Integer> DEFAULT_LEVELS = levels(
			"H", 1, "P", 2, "AP", 3, "AC", 4, "OBR", 3, "OBX", 4, "FAC", 5, "REG", 6, "ACT", 7, "ERR", 2, "L", 1);

	/**
	 * Parses a Base64 encoded HPRIM message.
	 */
	public static HPRIMMessage parseData(String content) {
		try {
			String decoded = new String(Base64.getDecoder().decode(content), StandardCharsets.UTF_8);
			return parse(decoded);
		} catch (Exception e) {
			logger.error("Error while parsing HPRIM content", e);
			throw new InternalErrorException("Error while parsing HPRIM content");
		}
	}

	private static HPRIMMessage parse(String raw) {
		HPRIMMessage message = new HPRIMMessage();
		List<String> physicalLines = toPhysicalLines(raw);

		if (physicalLines.isEmpty()) {
			return message;
		}

		char fieldSeparator = detectFieldSeparator(physicalLines.get(0));
		char componentSeparator = detectComponentSeparator(physicalLines.get(0));

		HPRIMSegment previousSegment = null;
		HPRIMSegment previousNonAddendumSegment = null;

		for (String physicalLine : physicalLines) {
			HPRIMSegment segment = parseSegment(physicalLine, fieldSeparator, componentSeparator);

			if ("H".equals(segment.getName())) {
				message.setMessageType(resolveMessageType(segment));
			}

			segment.setLevel(resolveLevel(message.getMessageType(), segment.getName(), previousSegment));
			message.addSegment(segment);

			if ("A".equals(segment.getName()) && previousNonAddendumSegment != null) {
				previousNonAddendumSegment.addAttachedSegment(segment);
			}

			if (!"A".equals(segment.getName())) {
				previousNonAddendumSegment = segment;
			}

			previousSegment = segment;
		}

		return message;
	}

	private static List<String> toPhysicalLines(String raw) {
		String normalized = raw.replace("\r\n", "\n").replace('\r', '\n');
		String[] lines = normalized.split("\n", -1);
		List<String> physicalLines = new ArrayList<>();

		for (String line : lines) {
			if (!line.isBlank()) {
				physicalLines.add(line);
			}
		}

		return physicalLines;
	}

	private static char detectFieldSeparator(String line) {
		return line.length() > 1 ? line.charAt(1) : '|';
	}

	private static char detectComponentSeparator(String line) {
		return line.length() > 2 ? line.charAt(2) : '^';
	}

	private static HPRIMSegment parseSegment(String physicalLine, char fieldSeparator, char componentSeparator) {
		int fieldSeparatorIndex = physicalLine.indexOf(fieldSeparator);
		String segmentName = fieldSeparatorIndex >= 0 ? physicalLine.substring(0, fieldSeparatorIndex) : physicalLine;

		HPRIMSegment segment = new HPRIMSegment(segmentName);
		segment.setDelimiters(fieldSeparator, componentSeparator);
		segment.setRawSegment(physicalLine);
		segment.setRawContent(fieldSeparatorIndex >= 0 ? physicalLine.substring(fieldSeparatorIndex + 1) : "");

		if ("A".equals(segmentName)) {
			segment.addField(new String[] {segment.getRawContent()});
			return segment;
		}

		String[] parts = physicalLine.split(Pattern.quote(String.valueOf(fieldSeparator)), -1);
		for (int i = 1; i < parts.length; i++) {
			String[] components = parts[i].split(Pattern.quote(String.valueOf(componentSeparator)), -1);
			segment.addField(components);
		}

		return segment;
	}

	private static String resolveMessageType(HPRIMSegment headerSegment) {
		if (headerSegment.getFields().size() <= 6) {
			return null;
		}

		String[] contextField = headerSegment.getFields().get(6);
		if (contextField.length == 0) {
			return null;
		}

		return blankToNull(contextField[0]);
	}

	private static int resolveLevel(String messageType, String segmentName, HPRIMSegment previousSegment) {
		if ("C".equals(segmentName) && previousSegment != null) {
			return previousSegment.getLevel();
		}

		Map<String, Integer> levelMap =
				messageType != null ? LEVELS_BY_MESSAGE_TYPE.getOrDefault(messageType, DEFAULT_LEVELS) : DEFAULT_LEVELS;

		Integer resolvedLevel = levelMap.get(segmentName);
		if (resolvedLevel != null) {
			return resolvedLevel;
		}

		return previousSegment != null ? previousSegment.getLevel() : 1;
	}

	private static String blankToNull(String value) {
		if (value == null) {
			return null;
		}

		String trimmed = value.trim();
		return trimmed.isEmpty() ? null : trimmed;
	}

	private static Map<String, Integer> levels(Object... values) {
		Map<String, Integer> levels = new LinkedHashMap<>();
		for (int i = 0; i < values.length; i += 2) {
			levels.put((String) values[i], (Integer) values[i + 1]);
		}
		return Map.copyOf(levels);
	}
}
