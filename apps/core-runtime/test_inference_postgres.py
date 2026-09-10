import os
import threading
import uuid

import pytest

psycopg = pytest.importorskip("psycopg")

DATABASE_URL = os.getenv("PULSE_STAGING_DATABASE_URL")
pytestmark = pytest.mark.skipif(
    not DATABASE_URL,
    reason="PULSE_STAGING_DATABASE_URL is required for the real-Postgres concurrency gate",
)


def connect():
    return psycopg.connect(DATABASE_URL, autocommit=True)


def test_ten_concurrent_claimers_have_exactly_one_winner():
    org_id = uuid.uuid4()
    model_id = uuid.uuid4()
    job_id = uuid.uuid4()

    with connect() as conn:
        with conn.cursor() as cur:
            cur.execute("insert into public.organizations (id, name) values (%s, %s)", (org_id, f"lease-test-{org_id}"))
            cur.execute(
                "insert into public.core_models (id, organization_id, name, framework) values (%s, %s, %s, 'onnx')",
                (model_id, org_id, f"lease-test-model-{model_id}"),
            )
            cur.execute(
                "insert into public.inference_jobs (id, model_id, organization_id, input_data, status) values (%s, %s, %s, %s::jsonb, 'queued')",
                (job_id, model_id, org_id, '{"data":[1.0]}'),
            )

    barrier = threading.Barrier(10)
    winners = []
    errors = []
    lock = threading.Lock()

    def worker(index):
        try:
            with connect() as conn:
                with conn.cursor() as cur:
                    barrier.wait(timeout=10)
                    cur.execute(
                        "select * from public.claim_inference_job(%s,%s,%s,%s)",
                        (job_id, org_id, f"test-worker-{index}-{uuid.uuid4()}", 60),
                    )
                    rows = cur.fetchall()
                    if rows:
                        with lock:
                            winners.append(rows[0][0])
        except Exception as exc:
            with lock:
                errors.append(exc)

    threads = [threading.Thread(target=worker, args=(i,)) for i in range(10)]
    for thread in threads:
        thread.start()
    for thread in threads:
        thread.join(timeout=15)

    try:
        assert not errors, errors
        assert winners == [job_id]
    finally:
        with connect() as conn:
            with conn.cursor() as cur:
                cur.execute("delete from public.inference_jobs where id=%s", (job_id,))
                cur.execute("delete from public.core_models where id=%s", (model_id,))
                cur.execute("delete from public.organizations where id=%s", (org_id,))
