package ca.uhn.fhir.jpa.starter.mapping.model;

import java.util.ArrayList;
import java.util.List;

public class HPRIMSegment {

	private final String name;
	private final List<String[]> fields;
	private final List<HPRIMSegment> attachedSegments;
	private int level;
	private int sequence = -1;
	private String rawSegment;
	private String rawContent;
	private char fieldSeparator = '|';
	private char componentSeparator = '^';

	public HPRIMSegment(String name) {
		this.name = name;
		this.fields = new ArrayList<>();
		this.attachedSegments = new ArrayList<>();
	}

	public String getName() {
		return name;
	}

	public List<String[]> getFields() {
		return fields;
	}

	public int getLevel() {
		return level;
	}

	public void setLevel(int level) {
		this.level = level;
	}

	public int getSequence() {
		return sequence;
	}

	public void setSequence(int sequence) {
		this.sequence = sequence;
	}

	public String getRawSegment() {
		if (rawSegment != null) {
			return rawSegment;
		}

		StringBuilder builder = new StringBuilder(name);
		for (String[] field : fields) {
			builder.append(fieldSeparator);
			builder.append(String.join(String.valueOf(componentSeparator), field));
		}
		return builder.toString();
	}

	public void setRawSegment(String rawSegment) {
		this.rawSegment = rawSegment;
	}

	public String getRawContent() {
		if (rawContent != null) {
			return rawContent;
		}

		if (rawSegment != null) {
			int separatorIndex = rawSegment.indexOf(fieldSeparator);
			if (separatorIndex >= 0 && separatorIndex + 1 <= rawSegment.length()) {
				return rawSegment.substring(separatorIndex + 1);
			}
			return "";
		}

		if (fields.isEmpty()) {
			return "";
		}

		StringBuilder builder = new StringBuilder();
		for (int i = 0; i < fields.size(); i++) {
			if (i > 0) {
				builder.append(fieldSeparator);
			}
			builder.append(String.join(String.valueOf(componentSeparator), fields.get(i)));
		}
		return builder.toString();
	}

	public void setRawContent(String rawContent) {
		this.rawContent = rawContent;
	}

	public void setDelimiters(char fieldSeparator, char componentSeparator) {
		this.fieldSeparator = fieldSeparator;
		this.componentSeparator = componentSeparator;
	}

	public void addField(String[] components) {
		fields.add(components);
	}

	public List<HPRIMSegment> getAttachedSegments() {
		return attachedSegments;
	}

	public List<HPRIMSegment> getAttachedSegments(String segmentName) {
		return attachedSegments.stream()
				.filter(segment -> segment.getName().equals(segmentName))
				.toList();
	}

	public void addAttachedSegment(HPRIMSegment segment) {
		attachedSegments.add(segment);
	}
}
