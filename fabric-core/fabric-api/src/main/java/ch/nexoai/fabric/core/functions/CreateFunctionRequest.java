package ch.nexoai.fabric.core.functions;

import lombok.Data;

@Data
public class CreateFunctionRequest {
    private String apiName;
    private String displayName;
    private String description;
    private String sourceCode;
    private String inputType;
    private String outputType;
}
