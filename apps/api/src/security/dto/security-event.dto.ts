import { IsIP, IsIn, IsObject, IsOptional, IsUUID, Max, Min, IsString } from 'class-validator';

export class SecurityEventDto {
  @IsUUID()
  organizationId!: string;

  @IsUUID()
  deviceId!: string;

  @IsIn(['intrusion', 'malware', 'anomaly', 'login_failure'])
  eventType!: string;

  @IsOptional()
  @IsIn(['info', 'low', 'medium', 'high', 'critical'])
  severity?: string;

  @IsOptional()
  @IsObject()
  details?: Record<string, unknown>;

  @IsOptional()
  @IsIP()
  sourceIp?: string;
}

export class ThreatIndicatorDto {
  @IsUUID()
  organizationId!: string;

  @IsString()
  indicator!: string;

  @IsIn(['ip', 'domain', 'hash', 'url'])
  type!: string;

  @IsOptional()
  @IsString()
  source?: string;

  @IsOptional()
  @Min(0)
  @Max(100)
  confidence?: number;
}

export class AnomalyDto {
  @IsUUID()
  deviceId!: string;

  @IsObject()
  telemetry!: Record<string, unknown>;
}
