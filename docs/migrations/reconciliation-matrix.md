# PULSE Migration Baseline Reconciliation

Status: **OPEN / BLOCKING**

Scope: production migration ledger vs `pulse-production-bulk-hardening` repository migration ledger.

## Authority model

- Production `supabase_migrations.schema_migrations` is authoritative for **what has actually happened in production**.
- The repository is authoritative for **future migrations**, but only after every migration is classified against the production baseline.
- Production migration history must not be rewritten, deleted, or manually fabricated.
- A migration is not considered reconciled from its filename alone; SQL/post-state comparison is required for divergent or unknown entries.

## Production ledger

The live Supabase project currently reports the following migration history, ordered by version:

| Version | Name |
|---|---|
| 20260909195417 | create_pulse_core_schema_v2 |
| 20260909202511 | add_pulse_engineering_schema |
| 20260909202943 | create_pulse_shield_security_module |
| 20260909203239 | harden_pulse_shield_indicator_uniqueness |
| 20260909203630 | harden_pulse_ontology_graph |
| 20260909204038 | create_pulse_auth_refresh_audit |
| 20260909204208 | add_refresh_token_organization_scope |
| 20260909205311 | create_pulse_find_core |
| 20260909205323 | create_pulse_ontology_pagerank |
| 20260909205403 | complete_pulse_find_vector_sync_schema |
| 20260909205536 | create_pulse_edge_sync_function |
| 20260909205548 | fix_pulse_edge_sync_function |
| 20260909205559 | fix_pulse_edge_sync_org_path |
| 20260909205711 | align_pulse_find_registration_consent_fields |
| 20260909205924 | add_pulse_domain_chrysalis_edge_v2 |
| 20260909210518 | create_universal_domain_tables_and_rls |
| 20260909211312 | create_pulse_ai_studio_app_forge |
| 20260909211810 | create_pulse_reclaim_recovery_service |
| 20260909212935 | create_pulse_insight_analytics |
| 20260909213507 | create_pulse_aegis_space_situational_awareness |
| 20260909214629 | create_pulse_intelligence_investigative_suite |
| 20260909215023 | create_pulse_core_inference_engine |
| 20260909220416 | create_pulse_ledger_trust_provenance |
| 20260909221637 | create_pulse_founder_executive_command_layer |
| 20260909221839 | harden_pulse_founder_confirmation_consumption |
| 20260909222703 | create_pulse_veritas_signal_analysis |
| 20260909223530 | create_pulse_key_crypto_service |
| 20260909224843 | create_pulse_key_universal_client_activation |
| 20260909230740 | create_pulse_event_bus_evb_029 |
| 20260909230816 | seed_pulse_event_bus_types_and_fix_rls |
| 20260909234445 | foundation_security_hardening |
| 20260910005128 | device_ingestion_idempotency |
| 20260910005440 | device_credential_expiry |
| 20260910105315 | drop_duplicate_indexes |
| 20260910105341 | add_missing_foreign_key_indexes |
| 20260910105537 | fix_rls_auth_initplan_reevaluation |
| 20260910155726 | key_operation_idempotency |
| 20260910182622 | pulse_contract_registry_v1 |
| 20260910185047 | align_pulse_event_registry_contract_v1 |
| 20260910185649 | dedupe_pulse_event_registry_indexes_v1 |
| 20260910185737 | retire_noncanonical_event_vocabulary_v1 |

Production migration count at inspection: **41** (fresh live query; last version `20260910185737`).

## Repository ledger — current hardening branch

The repository branch currently contains these migration files:

| Version | Name | Initial classification | Action |
|---|---|---|---|
| 202609100001 | foundation_security_hardening | divergent timestamp / SQL comparison required | compare against prod 20260909234445 |
| 20260910005128 | device_ingestion_idempotency | identical | none |
| 20260910005440 | device_credential_expiry | identical | none |
| 20260910006000 | batch5_academy_global_meet | repo-only | classify as future/experimental vs superseded |
| 20260910006001 | batch5_meet_worker | repo-only | classify as future/experimental vs superseded |
| 20260910007000 | platform_services_56_59 | repo-only | classify; production equivalent not yet evidenced |
| 20260910007001 | platform_services_56_59_event_vocabulary | repo-only | classify; production equivalent not yet evidenced |
| 20260910007002 | pulse_contract_registry_v1 | divergent timestamp / SQL comparison required | reconcile to prod 20260910182622 |
| 20260910008000 | storage_tenant_isolation | repo-only / explicitly staged | keep parked unless superseded; do not apply to prod |
| 20260910009000 | worker_atomic_leases | repo-only / explicitly staged | keep parked unless superseded; do not apply to prod |
| 20260910009100 | key_operation_context | repo-only / explicitly staged | keep parked unless superseded; do not apply to prod |
| 20260910009200 | event_delivery_atomic_leases | repo-only / explicitly staged | keep parked unless superseded; do not apply to prod |
| 20260910009300 | founder_action_context | repo-only / explicitly staged | keep parked unless superseded; do not apply to prod |
| 20260910111000 | appforge_atomic_provisioning | repo-only / explicitly staged | keep parked unless superseded; do not apply to prod |
| 20260910112000 | appforge_admission_limits | repo-only / explicitly staged | keep parked unless superseded; do not apply to prod |
| 20260910120000 | key_operation_idempotency | duplicate-intent candidate | compare SQL against prod 20260910155726 before removal |
| 20260910123000 | event_delivery_idempotency | repo-only / explicitly staged | keep parked unless superseded; do not apply to prod |
| 20260910124000 | event_delivery_operation_claim | repo-only / explicitly staged | keep parked unless superseded; do not apply to prod |
| 20260910220000 | align_pulse_event_registry_contract_v1 | duplicate-intent candidate | compare SQL against prod 20260910185047 before removal |
| 20260910220100 | dedupe_pulse_event_registry_indexes_v1 | duplicate-intent candidate | compare SQL against prod 20260910185649 before removal |
| 20260910220200 | retire_noncanonical_event_vocabulary_v1 | duplicate-intent candidate | compare SQL against prod 20260910185737 before removal |

Repository migration count at inspection: **21**.

## Production-only migrations

These are present in production but are not present under the same version/name in the current hardening branch:

| Production version | Name | Classification | Required evidence/action |
|---|---|---|---|
| 20260909195417 | create_pulse_core_schema_v2 | production-only | recover/reconcile historical migration source |
| 20260909202511 | add_pulse_engineering_schema | production-only | recover/reconcile historical migration source |
| 20260909202943 | create_pulse_shield_security_module | production-only | recover/reconcile historical migration source |
| 20260909203239 | harden_pulse_shield_indicator_uniqueness | production-only | recover/reconcile historical migration source |
| 20260909203630 | harden_pulse_ontology_graph | production-only | recover/reconcile historical migration source |
| 20260909204038 | create_pulse_auth_refresh_audit | production-only | recover/reconcile historical migration source |
| 20260909204208 | add_refresh_token_organization_scope | production-only | recover/reconcile historical migration source |
| 20260909205311 | create_pulse_find_core | production-only | recover/reconcile historical migration source |
| 20260909205323 | create_pulse_ontology_pagerank | production-only | recover/reconcile historical migration source |
| 20260909205403 | complete_pulse_find_vector_sync_schema | production-only | recover/reconcile historical migration source |
| 20260909205536 | create_pulse_edge_sync_function | production-only | recover/reconcile historical migration source |
| 20260909205548 | fix_pulse_edge_sync_function | production-only | recover/reconcile historical migration source |
| 20260909205559 | fix_pulse_edge_sync_org_path | production-only | recover/reconcile historical migration source |
| 20260909205711 | align_pulse_find_registration_consent_fields | production-only | recover/reconcile historical migration source |
| 20260909205924 | add_pulse_domain_chrysalis_edge_v2 | production-only | recover/reconcile historical migration source |
| 20260909210518 | create_universal_domain_tables_and_rls | production-only | recover/reconcile historical migration source |
| 20260909211312 | create_pulse_ai_studio_app_forge | production-only | recover/reconcile historical migration source |
| 20260909211810 | create_pulse_reclaim_recovery_service | production-only | recover/reconcile historical migration source |
| 20260909212935 | create_pulse_insight_analytics | production-only | recover/reconcile historical migration source |
| 20260909213507 | create_pulse_aegis_space_situational_awareness | production-only | recover/reconcile historical migration source |
| 20260909214629 | create_pulse_intelligence_investigative_suite | production-only | recover/reconcile historical migration source |
| 20260909215023 | create_pulse_core_inference_engine | production-only | recover/reconcile historical migration source |
| 20260909220416 | create_pulse_ledger_trust_provenance | production-only | recover/reconcile historical migration source |
| 20260909221637 | create_pulse_founder_executive_command_layer | production-only | recover/reconcile historical migration source |
| 20260909221839 | harden_pulse_founder_confirmation_consumption | production-only | recover/reconcile historical migration source |
| 20260909222703 | create_pulse_veritas_signal_analysis | production-only | recover/reconcile historical migration source |
| 20260909223530 | create_pulse_key_crypto_service | production-only | recover/reconcile historical migration source |
| 20260909224843 | create_pulse_key_universal_client_activation | production-only | recover/reconcile historical migration source |
| 20260909230740 | create_pulse_event_bus_evb_029 | production-only | recover/reconcile historical migration source |
| 20260909230816 | seed_pulse_event_bus_types_and_fix_rls | production-only | recover/reconcile historical migration source |
| 20260909234445 | foundation_security_hardening | production-only by version/name | compare with repo 202609100001 |
| 20260910105315 | drop_duplicate_indexes | production-only | recover exact historical SQL/effect |
| 20260910105341 | add_missing_foreign_key_indexes | production-only | recover exact historical SQL/effect |
| 20260910105537 | fix_rls_auth_initplan_reevaluation | production-only | recover exact historical SQL/effect |
| 20260910155726 | key_operation_idempotency | divergent intent candidate | compare with repo 20260910120000 |
| 20260910182622 | pulse_contract_registry_v1 | divergent timestamp | reconcile with repo 20260910007002 |
| 20260910185047 | align_pulse_event_registry_contract_v1 | divergent timestamp | reconcile with repo 20260910220000 |
| 20260910185649 | dedupe_pulse_event_registry_indexes_v1 | divergent timestamp | reconcile with repo 20260910220100 |
| 20260910185737 | retire_noncanonical_event_vocabulary_v1 | divergent timestamp | reconcile with repo 20260910220200 |

## Important findings

### 1. The divergence is larger than the registry block

Production contains the complete pre-Block-1 application history through `20260909234445`, while the current branch's migration directory contains only a subset of that history. Therefore the baseline problem cannot be solved by renaming the registry migration alone.

### 2. The foundation migration is also divergent

Production records `20260909234445_foundation_security_hardening`; the branch has `202609100001_foundation_security_hardening`. These must be compared before any migration rename or deletion.

### 3. Registry post-state is currently correct

Live production `event_types` has the ten canonical active v1 rows, populated payload schemas, envelope version 1, backward compatibility, and unique `(name,event_version)` and `schema_id` indexes. This does not prove migration replay correctness.

### 4. Several branch migrations are explicitly staged

The repository comments identify the storage isolation, worker lease, key-operation context, event-delivery lease, founder-action context, AppForge, and delivery-idempotency migrations as staged/controlled rather than production-applied. They must not be promoted merely to make migration counts line up.

### 5. Current production key-operation state is not enough to prove historical equivalence

Production has `key_operation_idempotency` at `20260910155726`, while the branch has a different `20260910120000` migration with the same name. Current indexes show an operation-id uniqueness structure on `crypto_operations`, but historical SQL equivalence still requires direct migration-source comparison.

### 6. Exact production migration SQL is the missing artifact

Current schema inspection can prove post-state, but cannot reconstruct historical intent with certainty where multiple migrations may have changed the same object. For production-only migrations, the exact source SQL should be recovered from the deployment/release history or an authoritative database backup/PITR artifact before backfilling the repository.

### 7. Fresh ledger verification corrected the earlier count

A fresh live query reports **41** production migrations, not 40. The ledger above is therefore the current authoritative count for this reconciliation snapshot.

## Current gate

**Migration baseline: RED.**

No migration file should be renamed, deleted, or rewritten solely from the current filename matrix. The next safe step is to recover/compare the SQL for the production-only and divergent migrations, then construct the canonical repository chain.
