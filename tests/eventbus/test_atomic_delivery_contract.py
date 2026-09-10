from pathlib import Path

ROOT = Path(__file__).parents[2]
MIGRATION = ROOT / "supabase/migrations/20260910009200_event_delivery_atomic_leases.sql"
OPERATION_MIGRATION = ROOT / "supabase/migrations/20260910124000_event_delivery_operation_claim.sql"
IDEMPOTENCY_MIGRATION = ROOT / "supabase/migrations/20260910123000_event_delivery_idempotency.sql"
SERVICE = ROOT / "apps/ontology/src/main/java/com/ivonix/pulse/ontology/eventbus/EventBusService.java"


def test_event_delivery_claim_is_atomic_and_service_role_only():
    sql = MIGRATION.read_text()
    assert "FOR UPDATE SKIP LOCKED" in sql
    assert "lease_until" in sql
    assert "worker_id" in sql
    assert "REVOKE ALL ON FUNCTION public.claim_event_delivery" in sql
    assert "GRANT EXECUTE ON FUNCTION public.claim_event_delivery" in sql


def test_delivery_operation_id_is_durable_and_returned_by_claim():
    operation_sql = OPERATION_MIGRATION.read_text()
    idempotency_sql = IDEMPOTENCY_MIGRATION.read_text()
    assert "operation_id uuid" in idempotency_sql
    assert "SET operation_id = gen_random_uuid()" in idempotency_sql
    assert "CREATE UNIQUE INDEX" in idempotency_sql
    assert "operation_id uuid" in operation_sql
    assert "d.operation_id" in operation_sql
    assert "stable operation_id" in operation_sql


def test_delivery_terminal_updates_are_owned_by_current_worker_and_operation():
    source = SERVICE.read_text()
    assert "claim_event_delivery" in source
    assert "operation_id=?" in source
    assert "worker_id=?" in source
    assert "lease_until > now()" in source
    assert "status in ('pending','failed')" in source
    assert "completed_at=now()" in source


def test_downstream_webhook_receives_stable_idempotency_key():
    source = SERVICE.read_text()
    assert 'header("Idempotency-Key", operationId.toString())' in source
    assert 'operation_id")' in source


def test_event_payload_is_bounded_before_persistence_and_webhook_delivery():
    source = SERVICE.read_text()
    assert "MAX_PAYLOAD_BYTES = 1024 * 1024" in source
    assert "Event payload exceeds 1 MiB" in source
    assert "Webhook payload exceeds 1 MiB" in source


def test_webhooks_never_follow_redirects_and_reject_non_public_destinations():
    source = SERVICE.read_text()
    assert "followRedirects(HttpClient.Redirect.NEVER)" in source
    assert "address.isLoopbackAddress()" in source
    assert "address.isLinkLocalAddress()" in source
    assert "address.isSiteLocalAddress()" in source
    assert "address.isMulticastAddress()" in source
    assert "first >= 0xfc && first <= 0xfd" in source
    assert "isIpv4Mapped" in source


def test_endpoint_is_revalidated_immediately_before_network_io():
    source = SERVICE.read_text()
    assert "Validate at subscription time and again immediately before network I/O." in source
    assert source.index("validateEndpoint(endpoint);") < source.index("HttpRequest req")
