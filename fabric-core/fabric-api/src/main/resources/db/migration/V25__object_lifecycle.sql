-- Object Lifecycle State Machines
-- Declarative state transitions per ObjectType

CREATE TABLE lifecycle_definitions (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id       UUID NOT NULL REFERENCES tenants(id),
    object_type_api_name VARCHAR(100) NOT NULL,
    state_property  VARCHAR(100) NOT NULL DEFAULT 'status',
    definition      JSONB NOT NULL,
    is_active       BOOLEAN DEFAULT TRUE,
    created_at      TIMESTAMPTZ DEFAULT NOW(),
    updated_at      TIMESTAMPTZ DEFAULT NOW(),
    UNIQUE(tenant_id, object_type_api_name)
);

ALTER TABLE ontology_objects
    ADD COLUMN IF NOT EXISTS current_state VARCHAR(100);

CREATE INDEX idx_objects_state ON ontology_objects(current_state)
    WHERE current_state IS NOT NULL;

ALTER TABLE lifecycle_definitions ENABLE ROW LEVEL SECURITY;
ALTER TABLE lifecycle_definitions FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation_lifecycle_definitions ON lifecycle_definitions
    USING (tenant_id = current_tenant_id() OR current_tenant_id() IS NULL);
