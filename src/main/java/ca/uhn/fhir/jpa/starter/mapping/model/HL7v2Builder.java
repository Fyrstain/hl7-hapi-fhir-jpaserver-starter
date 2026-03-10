package ca.uhn.fhir.jpa.starter.mapping.model;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class HL7v2Builder {

	private static final String FIELD_SEP = "|";
	private static final String REP_SEP = "~";
	private static final String COMP_SEP = "^";
	private static final String SUBCOMP_SEP = "&";
	private static final String SEG_SEP = "\r";

	/**
	 * Supported path forms:
	 * MSH-1
	 * OBR[+]-4-1
	 * OBX[=]-5
	 * OBX[=]-8[+]-1
	 * PID[0]-3[1]-4-2
	 * Groups:
	 * 1 = segment name
	 * 2 = segment index token (optional): +, =, or numeric
	 * 3 = field number (required)
	 * 4 = repetition index token (optional): +, =, or numeric
	 * 5 = component number (optional)
	 * 6 = subcomponent number (optional)
	 */
	private static final Pattern PATH_PATTERN = Pattern.compile(
		"^([A-Z0-9]{3})(?:\\[(\\+|=|\\d+)\\])?" +        // SEG[+]
			"-(\\d+)" +                                   // -FIELD
			"(?:\\[(\\+|=|\\d+)\\])?" +                   // [REP]
			"(?:-(\\d+))?" +                              // -COMP
			"(?:-(\\d+))?$"                               // -SUBCOMP
	);

	/**
	 * Message stored in true insertion order.
	 */
	private final List<SegmentInstance> message = new ArrayList<>();

	/**
	 * Last global segment index by segment name.
	 * Example: lastSegIndex.get("OBX") -> index in message list of the last OBX.
	 */
	private final Map<String, Integer> lastSegIndex = new HashMap<>();

	/**
	 * Last repetition index by "segmentGlobalIndex:fieldIndex"
	 */
	private final Map<String, Integer> lastRepIndex = new HashMap<>();

	/**
	 * Last component index by "segmentGlobalIndex:fieldIndex:repIndex"
	 */
	private final Map<String, Integer> lastCompIndex = new HashMap<>();

	/**
	 * Last subcomponent index by "segmentGlobalIndex:fieldIndex:repIndex:compIndex"
	 */
	private final Map<String, Integer> lastSubIndex = new HashMap<>();

	public HL7v2Builder() {
	}

	private static String nullToEmpty(String value) {
		return value == null ? "" : value;
	}

	private static List<String> splitPreserveEmpty(String value, String separatorRegex) {
		List<String> result = new ArrayList<>();
		if (value == null) {
			return result;
		}
		String[] parts = value.split(Pattern.quote(separatorRegex), -1);
		Collections.addAll(result, parts);
		return result;
	}

	private static <T> void ensureSize(List<T> list, int size, SupplierWithException<T> supplier) {
		while (list.size() < size) {
			try {
				list.add(supplier.get());
			} catch (Exception e) {
				throw new RuntimeException(e);
			}
		}
	}

	public void clear() {
		message.clear();
		lastSegIndex.clear();
		lastRepIndex.clear();
		lastCompIndex.clear();
		lastSubIndex.clear();
	}

	/**
	 * Adds or updates a value at the given HL7v2 path.
	 */
	public void putByPath(String path, String value) {
		Objects.requireNonNull(path, "path must not be null");

		path = normalizePath(path);
		if (path.matches("^[A-Z0-9]{3}(?:\\[(\\+|=|\\d+)\\])?-\\d+-\\+$")) {
			putByPathWithComponentAppend(path, value);
			return;
		}

		Matcher matcher = PATH_PATTERN.matcher(path);
		if (!matcher.matches()) {
			throw new IllegalArgumentException("Invalid HL7v2 path: " + path);
		}

		String segName = matcher.group(1);
		String segToken = matcher.group(2);
		int fieldNumber = Integer.parseInt(matcher.group(3));
		String repToken = matcher.group(4);
		String compToken = matcher.group(5);
		String subToken = matcher.group(6);

		if (fieldNumber < 1) {
			throw new IllegalArgumentException("Field number must be >= 1 in path: " + path);
		}

		int segIndex = resolveSegmentIndex(segName, segToken);
		SegmentInstance segment = message.get(segIndex);

		int fieldIndex = fieldNumber - 1;
		ensureSize(segment.fields, fieldIndex + 1, ArrayList::new);

		List<List<String>> repetitions = segment.fields.get(fieldIndex);

		int repIndex = resolveRepetitionIndex(segIndex, fieldIndex, repetitions, repToken);
		ensureSize(repetitions, repIndex + 1, ArrayList::new);

		List<String> components = repetitions.get(repIndex);

		if (compToken == null) {
			setSimpleFieldValue(segIndex, fieldIndex, repIndex, components, value);
			return;
		}

		int compIndex = Integer.parseInt(compToken) - 1;
		if (compIndex < 0) {
			throw new IllegalArgumentException("Component number must be >= 1 in path: " + path);
		}

		ensureSize(components, compIndex + 1, () -> null);
		rememberComponent(segIndex, fieldIndex, repIndex, compIndex);

		if (subToken == null) {
			components.set(compIndex, nullToEmpty(value));
			return;
		}

		int subIndex = Integer.parseInt(subToken) - 1;
		if (subIndex < 0) {
			throw new IllegalArgumentException("Subcomponent number must be >= 1 in path: " + path);
		}

		String existing = components.get(compIndex);
		List<String> subcomponents = splitPreserveEmpty(existing, SUBCOMP_SEP);

		ensureSize(subcomponents, subIndex + 1, () -> "");
		subcomponents.set(subIndex, nullToEmpty(value));

		components.set(compIndex, String.join(SUBCOMP_SEP, subcomponents));
		rememberSubcomponent(segIndex, fieldIndex, repIndex, compIndex, subIndex);
	}

	// -------------------------------------------------------------------------
	// Internal helpers
	// -------------------------------------------------------------------------
	private String normalizePath(String path) {
		if (path.endsWith(".value")) {
			path = path.substring(0, path.length() - ".value".length());
		}
		return path;
	}

	private void putByPathWithComponentAppend(String path, String value) {
		Matcher m = Pattern.compile("^([A-Z0-9]{3})(?:\\[(\\+|=|\\d+)\\])?-(\\d+)-\\+$").matcher(path);
		if (!m.matches()) {
			throw new IllegalArgumentException("Invalid HL7v2 component append path: " + path);
		}

		String segName = m.group(1);
		String segToken = m.group(2);
		int fieldNumber = Integer.parseInt(m.group(3));

		int segIndex = resolveSegmentIndex(segName, segToken);
		SegmentInstance segment = message.get(segIndex);

		int fieldIndex = fieldNumber - 1;
		ensureSize(segment.fields, fieldIndex + 1, ArrayList::new);

		List<List<String>> repetitions = segment.fields.get(fieldIndex);

		int repIndex = resolveRepetitionIndex(segIndex, fieldIndex, repetitions, null);
		ensureSize(repetitions, repIndex + 1, ArrayList::new);

		List<String> components = repetitions.get(repIndex);

		int compIndex = components.size();
		components.add(nullToEmpty(value));

		rememberComponent(segIndex, fieldIndex, repIndex, compIndex);
	}

	public void addSegment(String segmentName, List<String[]> fields) {
		SegmentInstance segment = new SegmentInstance(segmentName);

		if (fields != null) {
			for (String[] field : fields) {
				List<List<String>> repetitions = new ArrayList<>();

				List<String> components = new ArrayList<>();
				if (field != null) {
					for (String component : field) {
						components.add(component == null ? "" : component);
					}
				}

				repetitions.add(components);
				segment.fields.add(repetitions);
			}
		}

		message.add(segment);
		lastSegIndex.put(segmentName, message.size() - 1);
	}

	/**
	 * Returns the message as HL7 text, preserving global segment insertion order.
	 */
	public String build() {
		StringBuilder sb = new StringBuilder();

		for (SegmentInstance segment : message) {
			sb.append(segment.name);

			for (List<List<String>> repetitions : segment.fields) {
				sb.append(FIELD_SEP);

				List<String> renderedRepetitions = new ArrayList<>();
				for (List<String> components : repetitions) {
					renderedRepetitions.add(renderComponents(components));
				}

				sb.append(String.join(REP_SEP, renderedRepetitions));
			}

			sb.append(SEG_SEP);
		}

		return sb.toString();
	}

	/**
	 * Convenience alias if your code already calls toString() on the builder.
	 */
	@Override
	public String toString() {
		return build();
	}

	/**
	 * Optional accessor for debugging/tests.
	 */
	public List<String> getSegmentOrder() {
		List<String> result = new ArrayList<>();
		for (SegmentInstance segment : message) {
			result.add(segment.name);
		}
		return result;
	}

	private int resolveSegmentIndex(String segName, String segToken) {
		if (segToken == null) {
			Integer last = lastSegIndex.get(segName);
			if (last != null) {
				return last;
			}
			return createNewSegment(segName);
		}

		switch (segToken) {
			case "+":
				return createNewSegment(segName);

			case "=": {
				Integer last = lastSegIndex.get(segName);
				if (last == null) {
					throw new IllegalStateException("No previous segment for token [=]: " + segName);
				}
				return last;
			}

			default: {
				int occurrence = Integer.parseInt(segToken);
				int globalIndex = findSegmentOccurrence(segName, occurrence);
				if (globalIndex < 0) {
					throw new IllegalStateException(
						"Segment occurrence not found: " + segName + "[" + occurrence + "]"
					);
				}
				lastSegIndex.put(segName, globalIndex);
				return globalIndex;
			}
		}
	}

	private int createNewSegment(String segName) {
		SegmentInstance segment = new SegmentInstance(segName);
		message.add(segment);
		int index = message.size() - 1;
		lastSegIndex.put(segName, index);
		return index;
	}

	/**
	 * Finds the Nth occurrence of a segment by name in the global ordered list.
	 * 0-based occurrence.
	 */
	private int findSegmentOccurrence(String segName, int occurrence) {
		int count = -1;
		for (int i = 0; i < message.size(); i++) {
			if (segName.equals(message.get(i).name)) {
				count++;
				if (count == occurrence) {
					return i;
				}
			}
		}
		return -1;
	}

	private int resolveRepetitionIndex(
		int segIndex,
		int fieldIndex,
		List<List<String>> repetitions,
		String repToken
	) {
		String key = repKey(segIndex, fieldIndex);

		if (repToken == null) {
			if (repetitions.isEmpty()) {
				repetitions.add(new ArrayList<>());
				lastRepIndex.put(key, 0);
				return 0;
			}
			Integer last = lastRepIndex.get(key);
			if (last != null) {
				return last;
			}
			lastRepIndex.put(key, 0);
			return 0;
		}

		switch (repToken) {
			case "+": {
				int idx = repetitions.size();
				repetitions.add(new ArrayList<>());
				lastRepIndex.put(key, idx);
				return idx;
			}

			case "=": {
				Integer last = lastRepIndex.get(key);
				if (last == null) {
					if (repetitions.isEmpty()) {
						repetitions.add(new ArrayList<>());
						lastRepIndex.put(key, 0);
						return 0;
					}
					lastRepIndex.put(key, 0);
					return 0;
				}
				return last;
			}

			default: {
				int idx = Integer.parseInt(repToken);
				ensureSize(repetitions, idx + 1, ArrayList::new);
				lastRepIndex.put(key, idx);
				return idx;
			}
		}
	}

	private void setSimpleFieldValue(
		int segIndex,
		int fieldIndex,
		int repIndex,
		List<String> components,
		String value
	) {
		if (components.isEmpty()) {
			components.add(nullToEmpty(value));
		} else {
			components.set(0, nullToEmpty(value));
		}
		rememberComponent(segIndex, fieldIndex, repIndex, 0);
	}

	private void rememberComponent(int segIndex, int fieldIndex, int repIndex, int compIndex) {
		lastCompIndex.put(compKey(segIndex, fieldIndex, repIndex), compIndex);
	}

	private void rememberSubcomponent(
		int segIndex,
		int fieldIndex,
		int repIndex,
		int compIndex,
		int subIndex
	) {
		lastSubIndex.put(subKey(segIndex, fieldIndex, repIndex, compIndex), subIndex);
	}

	private String renderComponents(List<String> components) {
		List<String> safe = new ArrayList<>(components.size());
		for (String component : components) {
			safe.add(component == null ? "" : component);
		}
		return String.join(COMP_SEP, safe);
	}

	private String repKey(int segIndex, int fieldIndex) {
		return segIndex + ":" + fieldIndex;
	}

	private String compKey(int segIndex, int fieldIndex, int repIndex) {
		return segIndex + ":" + fieldIndex + ":" + repIndex;
	}

	private String subKey(int segIndex, int fieldIndex, int repIndex, int compIndex) {
		return segIndex + ":" + fieldIndex + ":" + repIndex + ":" + compIndex;
	}

	@FunctionalInterface
	private interface SupplierWithException<T> {
		T get() throws Exception;
	}

	private static class SegmentInstance {
		private final String name;
		private final List<List<List<String>>> fields = new ArrayList<>();

		private SegmentInstance(String name) {
			this.name = name;
		}
	}
}