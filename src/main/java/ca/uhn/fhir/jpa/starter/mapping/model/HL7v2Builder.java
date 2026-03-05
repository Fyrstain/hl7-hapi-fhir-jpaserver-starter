package ca.uhn.fhir.jpa.starter.mapping.model;

import java.util.*;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class HL7v2Builder {

	private static final String SEG_SEP = "\r";
	private static final String FIELD_SEP = "|";
	private static final String REP_SEP = "~";
	private static final String COMP_SEP = "^";
	private static final String SUBCOMP_SEP = "&";

	private static final Pattern PATH_PATTERN = Pattern.compile(
		"([A-Z0-9]{2,3})" +           // SEG
			"(?:\\[(\\d+|\\+|=)])?" +        // SEG[index|+|=]
			"-(\\d+)" +                      // FIELD
			"(?:\\[(\\d+|\\+|=)])?" +        // REP[index|+|=]
			"(?:-(\\d+|\\+|=))?" +           // COMP[index|+|=]
			"(?:-(\\d+|\\+|=))?" +           // SUB[index|+|=]
			"(?:\\.value)?"
	);
	private final Map<String, List<List<List<List<String>>>>> message = new LinkedHashMap<>();
	private final Map<String, Integer> lastSegIndex = new HashMap<>();
	private final Map<String, Integer> lastRepIndex = new HashMap<>();
	private final Map<String, Integer> lastCompIndex = new HashMap<>();
	private final Map<String, Integer> lastSubIndex = new HashMap<>();

	private static <T> void ensureSize(List<T> list, int size, Supplier<T> factory) {
		while (list.size() < size) {
			list.add(factory.get());
		}
	}

	private static List<String> nullToEmpty(List<String> list) {
		List<String> result = new ArrayList<>();
		for (String s : list) {
			result.add(s == null ? "" : s);
		}
		return result;
	}

	private static String keySeg(String seg) {
		return seg;
	}

	private static String keyRep(String seg, int field) {
		return seg + "-" + field;
	}

	private static String keyComp(String seg, int field, int rep) {
		return seg + "-" + field + "[" + rep + "]";
	}

	private static String keySub(String seg, int field, int rep, int comp) {
		return seg + "-" + field + "[" + rep + "]-" + comp;
	}

	public void putByPath(String path, String value) {
		Matcher m = PATH_PATTERN.matcher(path);
		if (!m.matches()) {
			throw new IllegalArgumentException("Invalid HL7v2 path: " + path);
		}

		String segmentName = m.group(1);

		Integer segToken = token(m.group(2));
		int fieldIndex = Integer.parseInt(m.group(3)) - 1;
		Integer repToken = token(m.group(4));
		Integer compToken = token(m.group(5));
		Integer subToken = token(m.group(6));

		message.computeIfAbsent(segmentName, k -> new ArrayList<>());
		List<List<List<List<String>>>> segments = message.get(segmentName);
		String segKey = keySeg(segmentName);

		// SEG
		int segIndex = resolveIndex(segToken, segments.size(), lastSegIndex.get(segKey));
		ensureSize(segments, segIndex + 1, ArrayList::new);
		lastSegIndex.put(segKey, segIndex);

		// FIELD (pas de token '=', c'est un numéro HL7)
		List<List<List<String>>> fields = segments.get(segIndex);
		ensureSize(fields, fieldIndex + 1, ArrayList::new);

		// REP
		List<List<String>> reps = fields.get(fieldIndex);
		String repKey = keyRep(segmentName, fieldIndex);
		int repIndex = resolveIndex(repToken, reps.size(), lastRepIndex.get(repKey));
		ensureSize(reps, repIndex + 1, ArrayList::new);
		lastRepIndex.put(repKey, repIndex);

		// COMP
		List<String> comps = reps.get(repIndex);
		String compKey = keyComp(segmentName, fieldIndex, repIndex);

		int compIndex;
		if (compToken != null) {
			if (compToken == -1) compIndex = comps.size(); // [+]
			else if (compToken == -2) {
				Integer last = lastCompIndex.get(compKey);
				// NEW: if '=' but nothing yet, create/use first component
				compIndex = (last != null) ? last : 0;
			} else compIndex = compToken - 1;              // HL7 1-based
		} else {
			compIndex = 0;
		}
		ensureSize(comps, compIndex + 1, () -> "");
		lastCompIndex.put(compKey, compIndex);

		// SUB (si tu veux gérer [=] aussi)
		if (subToken != null) {
			String subKey = keySub(segmentName, fieldIndex, repIndex, compIndex);

			String existing = comps.get(compIndex);
			List<String> subs = existing != null && !existing.isEmpty()
				? new ArrayList<>(Arrays.asList(existing.split(SUBCOMP_SEP, -1)))
				: new ArrayList<>();

			int subIndex;
			if (subToken == -1) subIndex = subs.size(); // [+]
			else if (subToken == -2) {
				Integer last = lastSubIndex.get(subKey);
				// NEW: if '=' but nothing yet, create/use first subcomponent
				subIndex = (last != null) ? last : 0;
			} else subIndex = subToken - 1; // HL7 1-based

			ensureSize(subs, subIndex + 1, () -> "");
			subs.set(subIndex, value);
			comps.set(compIndex, String.join(SUBCOMP_SEP, subs));

			lastSubIndex.put(subKey, subIndex);
		} else {
			comps.set(compIndex, value);
		}
	}

	public void addSegment(String segmentName, List<String[]> fields) {
		message.computeIfAbsent(segmentName, k -> new ArrayList<>());
		List<List<List<List<String>>>> segments = message.get(segmentName);

		List<List<List<String>>> segmentFields = new ArrayList<>();

		for (String[] fieldComponents : fields) {

			List<List<String>> repetitions = new ArrayList<>();

			List<String> components = new ArrayList<>(Arrays.asList(fieldComponents));

			repetitions.add(components);
			segmentFields.add(repetitions);
		}

		segments.add(segmentFields);
	}

	public String build() {
		StringBuilder sb = new StringBuilder();

		for (var entry : message.entrySet()) {
			String segmentName = entry.getKey();

			for (List<List<List<String>>> fields : entry.getValue()) {
				sb.append(segmentName);

				for (List<List<String>> reps : fields) {
					sb.append(FIELD_SEP);
					List<String> repStrings = new ArrayList<>();
					for (List<String> comps : reps) {
						repStrings.add(String.join(COMP_SEP, nullToEmpty(comps)));
					}
					sb.append(String.join(REP_SEP, repStrings));
				}
				sb.append(SEG_SEP);
			}
		}
		return sb.toString();
	}

	private Integer token(String raw) {
		if (raw == null) return null;
		if ("+".equals(raw)) return -1;
		if ("=".equals(raw)) return -2;
		return Integer.parseInt(raw);
	}

	private int resolveIndex(Integer token, int currentSize, Integer last) {
		if (token == null) return 0;
		if (token == -1) return currentSize;              // [+]
		if (token == -2) {                                // [=]
			// NEW: auto-create / fallback if no previous index exists
			if (last == null) {
				return 0;
			}
			return last;
		}
		return token;
	}

	@Override
	public String toString() {
		return build();
	}
}
