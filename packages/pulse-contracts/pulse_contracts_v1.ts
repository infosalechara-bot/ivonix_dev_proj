/** Canonical PULSE integration contracts v1. JSON Schema files are authoritative at runtime. */
export type PulseRole = "owner" | "admin" | "operator" | "viewer";
export type PulseActorType = "user" | "service" | "device" | "founder";

export interface PulseEvent<T = unknown> {
  eventId: string; // ULID
  eventType: string;
  eventVersion: number;
  occurredAt: string; // UTC, exactly YYYY-MM-DDTHH:mm:ss.SSSZ
  producer: string;
  aggregateType: string;
  aggregateId: string;
  organizationId: string | null;
  correlationId: string;
  causationId: string | null;
  payload: T;
  signature: string;
}

export interface PulseError {
  error: {
    code: string;
    message: string;
    messageKey: string;
    details?: Record<string, unknown>;
    requestId: string;
    docsUrl?: string;
  };
}

export interface PulsePage<T> {
  items: T[];
  cursor: string | null;
  hasMore: boolean;
  total?: number;
}

export interface PulseClaims {
  sub: string;
  organizationId: string;
  role: PulseRole;
  scopes: string[];
  actorType: PulseActorType;
  sessionId: string;
  iat: number;
  exp: number;
  iss: "pulse";
  aud: "pulse-api";
}

export const PULSE_API_PREFIX = "/api/v1" as const;
export const PULSE_REQUEST_ID_HEADER = "X-Request-ID" as const;
export const PULSE_CONTRACT_VERSION = "1.0" as const;
