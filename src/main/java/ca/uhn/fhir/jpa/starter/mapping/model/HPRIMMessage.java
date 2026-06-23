package ca.uhn.fhir.jpa.starter.mapping.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class HPRIMMessage {

	private final Map<String, List<HPRIMSegment>> segments = new LinkedHashMap<>();
	private final List<HPRIMSegment> orderedSegments = new ArrayList<>();
	private String messageType;

	public void addSegment(HPRIMSegment segment) {
		if (segment.getSequence() < 0) {
			segment.setSequence(orderedSegments.size());
		}
		orderedSegments.add(segment);
		segments.computeIfAbsent(segment.getName(), k -> new ArrayList<>()).add(segment);
	}

	public List<HPRIMSegment> getSegments(String name) {
		return segments.getOrDefault(name, List.of());
	}

	public Map<String, List<HPRIMSegment>> getAllSegments() {
		return segments;
	}

	public List<HPRIMSegment> getOrderedSegments() {
		return orderedSegments;
	}

	public List<HPRIMSegment> getSegmentsAtLevel(int level) {
		return orderedSegments.stream()
				.filter(segment -> segment.getLevel() == level)
				.toList();
	}

	public String getMessageType() {
		return messageType;
	}

	public void setMessageType(String messageType) {
		this.messageType = messageType;
	}
}
