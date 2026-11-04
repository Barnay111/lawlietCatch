package kz.behaviorguard.backend.risk;

public class ModelInsufficientDataException extends RuntimeException {
    public ModelInsufficientDataException(String message) {
        super(message);
    }
}
