package io.pulse.contracts.eventbus.v1;
public record PulseEvent<T>(String eventId,String eventType,int eventVersion,int envelopeVersion,String occurredAt,String producer,String aggregateType,String aggregateId,String organizationId,String correlationId,String causationId,T payload,String signature){public static final int ENVELOPE_VERSION=1;}
