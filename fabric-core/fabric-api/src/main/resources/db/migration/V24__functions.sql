-- Functions / Compute Layer
-- User-defined JavaScript code that runs in the ontology context

CREATE TABLE functions (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id       UUID NOT NULL REFERENCES tenants(id),
    api_name        VARCHAR(100) NOT NULL,
    display_name    VARCHAR(255) NOT NULL,
    description     TEXT,
    language        VARCHAR(20) NOT NULL DEFAULT 'javascript',
    source_code     TEXT NOT NULL,
    input_type      VARCHAR(100),
    output_type     VARCHAR(50),
    is_active       BOOLEAN DEFAULT TRUE,
    created_at      TIMESTAMPTZ DEFAULT NOW(),
    updated_at      TIMESTAMPTZ DEFAULT NOW(),
    UNIQUE(tenant_id, api_name)
);

CREATE TABLE function_executions (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id       UUID NOT NULL REFERENCES tenants(id),
    function_id     UUID NOT NULL REFERENCES functions(id),
    object_id       UUID,
    input_data      JSONB,
    output_data     JSONB,
    error_message   TEXT,
    duration_ms     INTEGER,
    status          VARCHAR(20) NOT NULL,
    executed_by     VARCHAR(255),
    executed_at     TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX idx_functions_tenant ON functions(tenant_id);
CREATE INDEX idx_executions_function ON function_executions(function_id);
CREATE INDEX idx_executions_tenant ON function_executions(tenant_id, executed_at DESC);

-- RLS policies
ALTER TABLE functions ENABLE ROW LEVEL SECURITY;
ALTER TABLE functions FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation_functions ON functions
    USING (tenant_id = current_tenant_id() OR current_tenant_id() IS NULL);

ALTER TABLE function_executions ENABLE ROW LEVEL SECURITY;
ALTER TABLE function_executions FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation_function_executions ON function_executions
    USING (tenant_id = current_tenant_id() OR current_tenant_id() IS NULL);
