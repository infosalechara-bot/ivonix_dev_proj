export const ENVELOPE_VERSION=1 as const;
export interface PulseEvent<T=unknown>{event_id:string;event_type:string;event_version:number;envelope_version:1;occurred_at:string;producer:string;aggregate_type:string;aggregate_id:string;org_id:string|null;correlation_id:string;causation_id:string|null;payload:T;signature:string;}
