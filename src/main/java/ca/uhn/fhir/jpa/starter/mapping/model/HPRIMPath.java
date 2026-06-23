package ca.uhn.fhir.jpa.starter.mapping.model;

import ca.uhn.fhir.rest.server.exceptions.InvalidRequestException;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class HPRIMPath {

	// Pattern : SEGMENT[index]-FIELD[rep]-COMP-SUB
	private static final Pattern PATH_PATTERN =
			Pattern.compile("([A-Z0-9]{1,3})(?:\\[(\\d+)])?(?:-(\\d+)(?:\\[(\\d+)])?(?:-(\\d+))?(?:-(\\d+))?)?");

	private final String segment;
	private final Integer segmentIndex;
	private final Integer field;
	private final Integer fieldRepetition;
	private final Integer component;
	private final Integer subComponent;
	private final boolean hasExplicitComponent;
	private final boolean rawSegmentReference;

	public HPRIMPath(String path) {
		Matcher matcher = PATH_PATTERN.matcher(path);
		if (!matcher.matches()) {
			throw new InvalidRequestException("Invalid HPRIM path: " + path);
		}

		this.segment = matcher.group(1);
		this.segmentIndex = matcher.group(2) != null ? Integer.parseInt(matcher.group(2)) : 0;
		Integer parsedField = null;
		boolean parsedRawSegmentReference = false;

		if (matcher.group(3) != null) {
			int fieldNumber = Integer.parseInt(matcher.group(3));
			if (fieldNumber == 0) {
				if (matcher.group(4) != null || matcher.group(5) != null || matcher.group(6) != null) {
					throw new InvalidRequestException("Invalid HPRIM raw segment path: " + path);
				}
				parsedRawSegmentReference = true;
			} else {
				parsedField = fieldNumber - 1; // HL7/HPRIM fields are 1-based
			}
		}

		this.field = parsedField;
		this.fieldRepetition =
				parsedRawSegmentReference ? 0 : matcher.group(4) != null ? Integer.parseInt(matcher.group(4)) : 0;
		this.component = matcher.group(5) != null ? Integer.parseInt(matcher.group(5)) - 1 : null; // components 1-based
		this.subComponent =
				matcher.group(6) != null ? Integer.parseInt(matcher.group(6)) - 1 : null; // subcomponents 1-based
		this.hasExplicitComponent = !parsedRawSegmentReference && path.matches(".+-\\d+-\\d+.*");
		this.rawSegmentReference = parsedRawSegmentReference;
	}

	public String getSegment() {
		return segment;
	}

	public Integer getSegmentIndex() {
		return segmentIndex;
	}

	public Integer getField() {
		return field;
	}

	public Integer getFieldRepetition() {
		return fieldRepetition;
	}

	public Integer getComponent() {
		return component;
	}

	public Integer getSubComponent() {
		return subComponent;
	}

	public boolean hasExplicitComponent() {
		return hasExplicitComponent;
	}

	public boolean isRawSegmentReference() {
		return rawSegmentReference;
	}

	@Override
	public String toString() {
		return "HPRIMPath{" + "segment='"
				+ segment + '\'' + ", segmentIndex="
				+ segmentIndex + ", field="
				+ field + ", fieldRepetition="
				+ fieldRepetition + ", rawSegmentReference="
				+ rawSegmentReference + ", component="
				+ component + ", subComponent="
				+ subComponent + '}';
	}
}
