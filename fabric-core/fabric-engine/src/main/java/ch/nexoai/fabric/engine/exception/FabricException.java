package ch.nexoai.fabric.engine.exception;

public class FabricException extends RuntimeException {

    public FabricException(String message) {
        super(message);
    }

    public FabricException(String message, Throwable cause) {
        super(message, cause);
    }
}
