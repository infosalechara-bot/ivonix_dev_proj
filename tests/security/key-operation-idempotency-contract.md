# KEY operation idempotency contract

## Invariants

1. Every externally retriable encrypt/decrypt request may supply a UUID `Idempotency-Key`.
2. `crypto_operations.operation_id` is unique when present.
3. The operation is bound to organization, key, actor, and operation type; a key cannot be replayed across tenants or capabilities.
4. A successful operation returns the persisted result for the same operation ID instead of invoking the crypto provider again.
5. A duplicate concurrent operation cannot create a second operation record.
6. Failed/in-progress operation IDs cannot silently produce a different result; callers must use a new operation ID.
7. Operation context and result are persisted in `crypto_operations` for audit/reconciliation.
8. Existing callers without an idempotency key remain backward compatible, but are not given retry deduplication.

## Negative cases

- malformed `Idempotency-Key`
- same operation ID with a different organization
- same operation ID with a different key
- same operation ID with a different actor
- same operation ID with encrypt vs decrypt
- concurrent duplicate requests
- crypto provider failure
- missing operation result

## Release evidence required

- schema migration applied successfully
- Java compilation/test suite passes
- concurrent duplicate integration test demonstrates exactly one crypto operation
- retry returns persisted result without a second provider invocation
- cross-tenant and context-mismatch tests fail closed
- audit row contains organization, caller, operation ID and terminal status

Existence of this contract is not certification. Production certification requires executed evidence.