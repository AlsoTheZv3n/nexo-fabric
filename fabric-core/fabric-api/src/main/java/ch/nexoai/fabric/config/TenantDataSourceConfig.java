package ch.nexoai.fabric.config;

import ch.nexoai.fabric.core.tenant.TenantContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.datasource.DelegatingDataSource;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;

/**
 * Wraps the auto-configured DataSource to set PostgreSQL RLS context
 * (app.tenant_id) on every connection acquired from the pool.
 */
@Configuration
@Slf4j
public class TenantDataSourceConfig {

    @Bean
    static BeanPostProcessor tenantDataSourcePostProcessor() {
        return new BeanPostProcessor() {
            @Override
            public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
                if ("dataSource".equals(beanName) && bean instanceof DataSource ds) {
                    log.info("Wrapping DataSource with TenantAwareDataSource for RLS support");
                    return new TenantAwareDataSource(ds);
                }
                return bean;
            }
        };
    }

    static class TenantAwareDataSource extends DelegatingDataSource {

        TenantAwareDataSource(DataSource targetDataSource) {
            super(targetDataSource);
        }

        @Override
        public Connection getConnection() throws SQLException {
            Connection conn = super.getConnection();
            applyTenantContext(conn);
            return conn;
        }

        @Override
        public Connection getConnection(String username, String password) throws SQLException {
            Connection conn = super.getConnection(username, password);
            applyTenantContext(conn);
            return conn;
        }

        private void applyTenantContext(Connection conn) throws SQLException {
            UUID tenantId = TenantContext.getTenantIdOrNull();
            try (Statement stmt = conn.createStatement()) {
                if (tenantId != null) {
                    // UUID.toString() is safe — only hex digits and dashes
                    stmt.execute("SET app.tenant_id = '" + tenantId + "'");
                } else {
                    stmt.execute("RESET app.tenant_id");
                }
            }
        }
    }
}
