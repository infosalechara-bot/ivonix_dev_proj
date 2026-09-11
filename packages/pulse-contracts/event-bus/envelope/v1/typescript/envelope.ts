export const ENVELOPE_VERSION=1 as const;
export interface PulseEvent<T=unknown>{eventId:string;eventType:string;eventVersion:number;occurredAt:string;producer:string;aggregateType:string;aggregateId:string;organizationId:string|null;correlationId:string;causationId:string|null;payload:T;signature:string;}
