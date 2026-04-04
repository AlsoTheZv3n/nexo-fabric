package ch.nexoai.fabric.core.exception;

public class DuplicateApiNameException extends OntologyException {
    public DuplicateApiNameException(String apiName) {
        super("ApiName already exists: " + apiName);
    }
}
