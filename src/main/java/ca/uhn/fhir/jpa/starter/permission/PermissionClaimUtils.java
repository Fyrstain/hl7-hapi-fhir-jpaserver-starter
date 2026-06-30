package ca.uhn.fhir.jpa.starter.permission;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class PermissionClaimUtils {

	private PermissionClaimUtils() {
	}

	public static Object getClaimValue(Map<String, Object> claims, String path) {
		if (claims == null || claims.isEmpty() || isBlank(path)) {
			return null;
		}

		Object current = claims;
		for (String segment : path.split("\\.")) {
			if (!(current instanceof Map<?, ?> currentMap)) {
				return null;
			}
			current = currentMap.get(segment);
			if (current == null) {
				return null;
			}
		}
		return current;
	}

	public static String getFirstString(Map<String, Object> claims, List<String> paths) {
		for (String path : paths) {
			String value = asString(getClaimValue(claims, path));
			if (!isBlank(value)) {
				return value;
			}
		}
		return null;
	}

	public static Set<String> getStringSet(Map<String, Object> claims, List<String> paths) {
		Set<String> values = new LinkedHashSet<>();
		for (String path : paths) {
			collectStrings(getClaimValue(claims, path), values);
		}
		return values;
	}

	public static List<Object> getRawValues(Map<String, Object> claims, List<String> paths) {
		List<Object> values = new ArrayList<>();
		for (String path : paths) {
			Object value = getClaimValue(claims, path);
			if (value != null) {
				values.add(value);
			}
		}
		return values;
	}

	public static String asString(Object value) {
		if (value == null) {
			return null;
		}
		if (value instanceof String stringValue) {
			return stringValue;
		}
		if (value instanceof Number || value instanceof Boolean) {
			return value.toString();
		}
		return null;
	}

	private static void collectStrings(Object value, Set<String> values) {
		if (value == null) {
			return;
		}
		if (value instanceof String stringValue) {
			if (stringValue.contains(" ")) {
				for (String split : stringValue.split("\\s+")) {
					if (!isBlank(split)) {
						values.add(split);
					}
				}
			} else if (!stringValue.isBlank()) {
				values.add(stringValue);
			}
			return;
		}
		if (value instanceof Collection<?> collection) {
			for (Object item : collection) {
				collectStrings(item, values);
			}
			return;
		}
		if (value.getClass().isArray()) {
			Object[] array = (Object[]) value;
			for (Object item : array) {
				collectStrings(item, values);
			}
			return;
		}
		if (!(value instanceof Map<?, ?>)) {
			values.add(Objects.toString(value));
		}
	}

	private static boolean isBlank(String value) {
		return value == null || value.isBlank();
	}
}
