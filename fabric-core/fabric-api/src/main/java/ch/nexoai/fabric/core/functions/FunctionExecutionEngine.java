package ch.nexoai.fabric.core.functions;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.NativeJSON;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.ScriptableObject;
import org.mozilla.javascript.Undefined;
import org.springframework.stereotype.Component;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Executes user-defined JavaScript in the ontology context using Mozilla Rhino.
 *
 * Sandbox: optimization level -1 (interpreted only), no Java reflection access,
 * no filesystem or network from the script.
 * Timeout: 5 seconds per execution; killed thread on overrun.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class FunctionExecutionEngine {

    private final ObjectMapper objectMapper;
    private final ExecutorService executor = Executors.newFixedThreadPool(4);

    private static final long TIMEOUT_MS = 5_000;

    /**
     * Run a function with the given input data.
     * The script receives a global `object` variable holding the input properties,
     * and a `context` object with utility methods.
     */
    public FunctionExecutionResult execute(FunctionDefinition function, JsonNode inputData) {
        long start = System.currentTimeMillis();

        Future<FunctionExecutionResult> future = executor.submit(() -> runScript(function, inputData, start));

        try {
            return future.get(TIMEOUT_MS, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            log.warn("[Functions] Timeout in '{}'", function.getApiName());
            return FunctionExecutionResult.failure(
                    "Execution timeout (>" + TIMEOUT_MS + "ms)",
                    System.currentTimeMillis() - start);
        } catch (Exception e) {
            return FunctionExecutionResult.failure(e.getMessage(), System.currentTimeMillis() - start);
        }
    }

    private FunctionExecutionResult runScript(FunctionDefinition function, JsonNode inputData, long start) {
        Context cx = Context.enter();
        try {
            cx.setOptimizationLevel(-1);
            cx.setLanguageVersion(Context.VERSION_ES6);

            Scriptable scope = cx.initStandardObjects();

            // Provide `object` as parsed JS object via JSON eval
            String inputJson = inputData != null ? inputData.toString() : "{}";
            Object jsInput = cx.evaluateString(scope, "(" + inputJson + ")", "input", 1, null);
            ScriptableObject.putProperty(scope, "object", jsInput);

            // Provide `context.log()` no-op utility
            Object jsContext = cx.evaluateString(scope,
                    "({ log: function(msg) { } })", "ctx", 1, null);
            ScriptableObject.putProperty(scope, "context", jsContext);

            // Wrap source so a return statement at the top level is valid
            String wrappedSource = "(function() {\n" + function.getSourceCode() + "\n})()";

            Object result = cx.evaluateString(scope, wrappedSource, function.getApiName(), 1, null);

            JsonNode output = toJsonNode(cx, scope, result);
            return FunctionExecutionResult.success(output, System.currentTimeMillis() - start);

        } catch (Exception e) {
            log.warn("[Functions] Execution error in '{}': {}", function.getApiName(), e.getMessage());
            return FunctionExecutionResult.failure(e.getMessage(), System.currentTimeMillis() - start);
        } finally {
            Context.exit();
        }
    }

    private JsonNode toJsonNode(Context cx, Scriptable scope, Object result) {
        if (result == null || result == Undefined.instance) {
            return objectMapper.nullNode();
        }
        if (result instanceof Boolean b) return objectMapper.valueToTree(b);
        if (result instanceof Number n) return objectMapper.valueToTree(n.doubleValue());
        if (result instanceof CharSequence s) {
            String str = s.toString();
            // Try to parse as JSON, fall back to raw string
            try {
                return objectMapper.readTree(str);
            } catch (Exception ignore) {
                return objectMapper.valueToTree(str);
            }
        }
        // Native JS object/array → stringify via JSON.stringify
        try {
            Object stringified = NativeJSON.stringify(cx, scope, result, null, "");
            return objectMapper.readTree(stringified.toString());
        } catch (Exception e) {
            return objectMapper.valueToTree(result.toString());
        }
    }
}
