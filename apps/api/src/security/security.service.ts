import { BadRequestException, ForbiddenException, Inject, Injectable, UnauthorizedException } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import { createHash } from 'node:crypto';
import { SupabaseClient } from '@supabase/supabase-js';
import axios from 'axios';
import { SecurityEventDto, ThreatIndicatorDto } from './dto/security-event.dto';

@Injectable()
export class SecurityService {
  constructor(@Inject('SUPABASE_CLIENT') private readonly supabase: SupabaseClient, private readonly config: ConfigService) {}

  async authenticate(accessToken: string | undefined): Promise<string> {
    if (!accessToken) throw new UnauthorizedException('Bearer token required');
    const { data, error } = await this.supabase.auth.getUser(accessToken);
    if (error || !data.user?.id) throw new UnauthorizedException('Invalid or expired access token');
    return data.user.id;
  }

  async authorizeOrganization(userId: string, organizationId: string): Promise<void> {
    const { data, error } = await this.supabase.from('organization_members')
      .select('user_id').eq('organization_id', organizationId).eq('user_id', userId).maybeSingle();
    if (error) throw new ForbiddenException('Organization authorization check failed');
    if (!data) throw new ForbiddenException('User is not a member of this organization');
  }

  async authorizeDevice(userId: string, deviceId: string): Promise<string> {
    const { data: device, error } = await this.supabase.from('devices')
      .select('organization_id').eq('id', deviceId).maybeSingle();
    if (error || !device?.organization_id) throw new ForbiddenException('Device is not accessible');
    await this.authorizeOrganization(userId, device.organization_id);
    return device.organization_id;
  }

  async ingestEvent(dto: SecurityEventDto, userId: string) {
    await this.authorizeOrganization(userId, dto.organizationId);
    await this.authorizeDevice(userId, dto.deviceId);
    const { data, error } = await this.supabase.from('security_events').insert({
      organization_id: dto.organizationId, device_id: dto.deviceId, event_type: dto.eventType,
      severity: dto.severity ?? 'medium', details: dto.details ?? {}, source_ip: dto.sourceIp,
    }).select().single();
    if (error) throw new BadRequestException(error.message);
    return data;
  }

  async lookupThreat(indicator: string, type: string, userId: string) {
    const { data: memberships, error: membershipError } = await this.supabase.from('organization_members')
      .select('organization_id').eq('user_id', userId);
    if (membershipError) throw new ForbiddenException('Organization authorization check failed');
    const organizationIds = (memberships ?? []).map((m) => m.organization_id).filter(Boolean);
    if (!organizationIds.length) return null;
    const { data, error } = await this.supabase.from('threat_indicators')
      .select('indicator,type,source,confidence,active').eq('indicator', indicator).eq('type', type)
      .eq('active', true).in('organization_id', organizationIds).maybeSingle();
    if (error) throw new BadRequestException(error.message);
    return data;
  }

  async addThreatIndicator(dto: ThreatIndicatorDto, userId: string) {
    await this.authorizeOrganization(userId, dto.organizationId);
    const { data, error } = await this.supabase.from('threat_indicators').insert({
      organization_id: dto.organizationId, indicator: dto.indicator, type: dto.type,
      source: dto.source, confidence: dto.confidence, active: true,
    }).select().single();
    if (error) throw new BadRequestException(error.message);
    return data;
  }

  async scanFileHash(hash: string) {
    if (!/^[a-fA-F0-9]{32,128}$/.test(hash)) throw new BadRequestException('Invalid file hash');
    const apiKey = this.config.get<string>('VIRUSTOTAL_API_KEY');
    if (!apiKey) throw new BadRequestException('VirusTotal integration is not configured');
    try {
      const response = await axios.get(`https://www.virustotal.com/api/v3/files/${hash}`, {
        headers: { 'x-apikey': apiKey }, timeout: 10000,
      });
      const stats = response.data.data.attributes.last_analysis_stats;
      return { hash, malicious: stats.malicious > 0, stats };
    } catch { throw new BadRequestException('Threat intelligence lookup failed'); }
  }

  async detectAnomaly(deviceId: string, telemetry: Record<string, unknown>, userId: string) {
    const organizationId = await this.authorizeDevice(userId, deviceId);
    const temperature = Number(telemetry.temperature);
    if (Number.isFinite(temperature) && temperature > 90) {
      return this.ingestEvent({ organizationId, deviceId, eventType: 'anomaly', severity: 'high',
        details: { type: 'high_temperature', value: temperature } }, userId);
    }
    return null;
  }

  static fingerprint(value: string) { return createHash('sha256').update(value).digest('hex'); }
}
