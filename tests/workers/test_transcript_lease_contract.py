from pathlib import Path

ROOT = Path(__file__).parents[2]
MIGRATION = ROOT / "supabase/migrations/20260910009000_worker_atomic_leases.sql"
WORKER = ROOT / "apps/transcript-worker/transcript_worker.py"
STORAGE = ROOT / "supabase/migrations/20260910008000_storage_tenant_isolation.sql"


def test_atomic_claim_uses_skip_locked_and_bounded_lease():
    sql = MIGRATION.read_text()
    assert "FOR UPDATE SKIP LOCKED" in sql
    assert "transcription_lease_until" in sql
    assert "transcription_attempts" in sql
    assert "REVOKE ALL ON FUNCTION public.claim_meet_recording" in sql
    assert "GRANT EXECUTE ON FUNCTION public.claim_meet_recording" in sql


def test_worker_claims_through_rpc_and_binds_terminal_update_to_worker():
    source = WORKER.read_text()
    assert "/rest/v1/rpc/claim_meet_recording" in source
    assert "p_worker_id" in source
    assert "transcription_worker_id" in source
    assert "transcription_lease_until" in source
    assert "eq.{WORKER_ID}" in source


def test_worker_lease_has_safe_bounds():
    source = WORKER.read_text()
    assert "max(30, min(int(os.getenv('LEASE_SECONDS', '300')), 3600))" in source


def test_worker_uses_private_canonical_bucket_and_tenant_path():
    source = WORKER.read_text()
    storage = STORAGE.read_text()
    assert "STORAGE_BUCKET = os.getenv('TRANSCRIPT_STORAGE_BUCKET', 'pulse-assets')" in source
    assert "storage/v1/object/{STORAGE_BUCKET}/{path}" in source
    assert "path.startswith(f'{organization_id}/')" in source
    assert "bucket_id = 'pulse-assets'" in storage
    assert "public.is_org_member" in storage
    assert "public = false" in storage
