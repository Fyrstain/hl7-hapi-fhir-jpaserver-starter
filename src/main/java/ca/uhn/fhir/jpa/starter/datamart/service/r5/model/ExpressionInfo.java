package ca.uhn.fhir.jpa.starter.datamart.service.r5.model;

import org.hl7.fhir.r5.model.Parameters;

/**
 * Value object that captures an expression name and the library id where it is defined.
 * Equality and hashCode are based on both fields, enabling duplicate filtering.
 */
public class ExpressionInfo {
	public String expressionName;
	public String libraryId;
	public Parameters parameters;

	public ExpressionInfo(String expressionName, String libraryId, Parameters parameters) {
		this.expressionName = expressionName;
		this.libraryId = libraryId;
		this.parameters = parameters;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;
		ExpressionInfo that = (ExpressionInfo) o;
		return java.util.Objects.equals(expressionName, that.expressionName) &&
			java.util.Objects.equals(libraryId, that.libraryId);
	}

	@Override
	public int hashCode() {
		return java.util.Objects.hash(expressionName, libraryId);
	}
}

