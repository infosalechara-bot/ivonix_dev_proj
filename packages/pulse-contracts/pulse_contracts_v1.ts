/** Canonical PULSE integration contracts v1. JSON Schema files are authoritative at runtime. */
export type PulseRole = "owner" | "admin" | "operator" | "viewer";
export type PulseActorType = "user" | "service" | "device" | "founder";

export interface PulseEvent<T = unknown> {
  event_id: string; // ULID
  event_type: string;
  event_version: number;
  envelope_version: 1;
  occurred_at: string; // UTC, exactly YYYY-MM-DDTHH:mm:ss.SSSZ
  producer: string;
  aggregate_type: string;
  aggregate_id: string;
  org_id: string | null;
  correlation_id: string;
  causation_id: string | null;
  payload: T;
  signature: string;
}

export interface PulseError {
  error: {
    code: string;
    message: string;
    message_key: string;
    details?: Record<string, unknown>;
    request_id: string;
    docs_url?: string;
  };
  error_legacy?: string;
  fields?: Record<string, unknown>;
}

export interface PulsePage<T> {
  items: T[];
  cursor: string | null;
  has_more: boolean;
  total: number | null;
}

export interface PulseClaims {
  sub: string;
  iat: number;
  exp: number;
  organization_id: string;
  org_id: string;
  role: PulseRole;
  scopes: string[];
  actor_type: PulseActorType;
  session_id: string;
  iss: "pulse";
  aud: "pulse-api";
}

export const PULSE_API_PREFIX = "/api/v1" as const;
export const PULSE_REQUEST_ID_HEADER = "X-Request-ID" as const;
export const PULSE_CONTRACT_VERSION = "1.0" as const;
