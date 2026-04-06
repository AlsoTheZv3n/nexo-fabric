package ch.nexoai.fabric.core.functions;

public class FunctionNotFoundException extends RuntimeException {
    public FunctionNotFoundException(String apiName) {
        super("Function not found: " + apiName);
    }
}
