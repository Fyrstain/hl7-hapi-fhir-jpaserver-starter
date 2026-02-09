package ca.uhn.fhir.jpa.starter.datamart.service.r5.model;

/**
 * Export modes supported by $export-datamart.
 */
public enum ExportType {
	CSV("text/csv"),
	REST("rest"),
	BULK("bulk");

	private final String code;

	ExportType(String code) {
		this.code = code;
	}

	public static ExportType fromCode(String code) {
		if (code == null) throw new IllegalArgumentException("The export type is missing");
		String c = code.trim().toLowerCase();
		for (ExportType type : values()) {
			if (type.code.equalsIgnoreCase(c)) return type;
		}

		if ("text/csv".equalsIgnoreCase(c) || "csv".equalsIgnoreCase(c)) return CSV;
		throw new IllegalArgumentException("Unsupported export type: " + code);
	}

	public String code() {
		return code;
	}
}
