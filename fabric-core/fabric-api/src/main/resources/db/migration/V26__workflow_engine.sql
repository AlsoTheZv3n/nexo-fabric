-- Workflow Engine: extend workflow_runs for resumable execution

ALTER TABLE workflow_runs ADD COLUMN IF NOT EXISTS current_step VARCHAR(100);
ALTER TABLE workflow_runs ADD COLUMN IF NOT EXISTS resume_at TIMESTAMPTZ;
ALTER TABLE workflow_runs ADD COLUMN IF NOT EXISTS context JSONB DEFAULT '{}';

CREATE INDEX IF NOT EXISTS idx_workflow_runs_resume
    ON workflow_runs(status, resume_at)
    WHERE status = 'WAITING';

CREATE INDEX IF NOT EXISTS idx_workflow_runs_tenant
    ON workflow_runs(tenant_id);

ALTER TABLE workflows ENABLE ROW LEVEL SECURITY;
ALTER TABLE workflows FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS tenant_isolation_workflows ON workflows;
CREATE POLICY tenant_isolation_workflows ON workflows
    USING (tenant_id = current_tenant_id() OR current_tenant_id() IS NULL);

ALTER TABLE workflow_runs ENABLE ROW LEVEL SECURITY;
ALTER TABLE workflow_runs FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS tenant_isolation_workflow_runs ON workflow_runs;
CREATE POLICY tenant_isolation_workflow_runs ON workflow_runs
    USING (tenant_id = current_tenant_id() OR current_tenant_id() IS NULL);
