package ch.nexoai.fabric.core.tenant;

import ch.nexoai.fabric.core.apikey.ApiKeyService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Authenticates requests using API keys (X-API-Key header or query param).
 * Runs after JwtAuthFilter — only activates if no JWT auth was set.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ApiKeyAuthFilter extends OncePerRequestFilter {

    private final ApiKeyService apiKeyService;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                     FilterChain filterChain) throws ServletException, IOException {

        // Skip if already authenticated via JWT
        if (SecurityContextHolder.getContext().getAuthentication() != null
                && SecurityContextHolder.getContext().getAuthentication().isAuthenticated()) {
            filterChain.doFilter(request, response);
            return;
        }

        String apiKey = extractApiKey(request);
        if (apiKey == null) {
            filterChain.doFilter(request, response);
            return;
        }

        Map<String, Object> keyData = apiKeyService.validateKey(apiKey);
        if (keyData == null) {
            filterChain.doFilter(request, response);
            return;
        }

        UUID tenantId = UUID.fromString(keyData.get("tenant_id").toString());
        String keyName = keyData.get("name").toString();

        TenantContext.setTenantId(tenantId);
        TenantContext.setCurrentUser("apikey:" + keyName);
        TenantContext.setCurrentRole("MEMBER");

        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                "apikey:" + keyName,
                null,
                List.of(new SimpleGrantedAuthority("ROLE_MEMBER"))
        );
        auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        SecurityContextHolder.getContext().setAuthentication(auth);

        log.debug("Authenticated via API key: {}", keyData.get("key_prefix"));

        try {
            filterChain.doFilter(request, response);
        } finally {
            TenantContext.clear();
        }
    }

    private String extractApiKey(HttpServletRequest request) {
        String header = request.getHeader("X-API-Key");
        if (header != null && header.startsWith("nxo_")) {
            return header;
        }
        String param = request.getParameter("api_key");
        if (param != null && param.startsWith("nxo_")) {
            return param;
        }
        return null;
    }
}
