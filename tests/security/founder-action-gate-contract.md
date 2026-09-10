# Founder Action Gate Contract

This contract is a release gate for every operation classified as critical by FND-026.

## Required enforcement

A critical operation MUST, before its irreversible side effect:

1. authenticate the acting founder identity;
2. resolve the target organization from server-side authorization context;
3. require an approval confirmation bound to the exact action, organization, resource type, and resource ID;
4. atomically consume the approval with a single database conditional update;
5. reject missing, expired, already-consumed, organization-mismatched, actor-mismatched, or context-mismatched approvals;
6. persist the signed consumption receipt before reporting success;
7. include the operation/receipt identifier in the immutable audit trail.

## Prohibited patterns

- UI-only founder checks.
- Boolean `isFounder` checks without an approval confirmation.
- Client-supplied organization identity used as the authorization source.
- Consuming an approval before validating its complete resource context.
- Executing the side effect first and validating the founder approval afterward.
- Reusing one confirmation for multiple critical operations.
- Treating a shared secret as proof of founder identity.

## Mandatory negative cases

Each critical endpoint/service must have tests proving rejection for:

- no confirmation;
- nonexistent confirmation;
- expired confirmation;
- already consumed confirmation;
- wrong founder;
- wrong organization;
- wrong action;
- wrong resource type;
- wrong resource ID;
- concurrent double-consumption.

## Release evidence

Production certification requires a call-site inventory mapping every critical operation to:

`operation -> implementation -> gate invocation -> receipt verification -> audit event -> integration test`.

An operation is **not certified** merely because `FounderActionGate` exists in the repository.
