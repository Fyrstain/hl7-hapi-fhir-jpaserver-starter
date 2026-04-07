package ca.uhn.fhir.jpa.starter.mapping.model.exception;

public class MatchboxTransformException extends RuntimeException {

    public MatchboxTransformException(String message, Throwable cause) {
        super(message, cause);
    }
}