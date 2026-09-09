import { BadRequestException, Inject, Injectable } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import { createHash } from 'node:crypto';
import { SupabaseClient } from '@supabase/supabase-js';
import axios from 'axios';
import { SecurityEventDto, ThreatIndicatorDto } from './dto/security-event.dto';

@Injectable()
export class SecurityService {
  constructor(@Inject('SUPABASE_CLIENT') private readonly supabase: SupabaseClient, private readonly config: ConfigService) {}

  async ingestEvent(dto: SecurityEventDto) {
    const { data, error } = await this.supabase.from('security_events').insert({
      organization_id: dto.organizationId, device_id: dto.deviceId, event_type: dto.eventType,
      severity: dto.severity ?? 'medium', details: dto.details ?? {}, source_ip: dto.sourceIp,
    }).select().single();
    if (error) throw new BadRequestException(error.message);
    return data;
  }

  async lookupThreat(indicator: string, type: string) {
    const { data, error } = await this.supabase.from('threat_indicators').select('*')
      .eq('indicator', indicator).eq('type', type).eq('active', true).maybeSingle();
    if (error) throw new BadRequestException(error.message);
    return data;
  }

  async addThreatIndicator(dto: ThreatIndicatorDto) {
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

  async detectAnomaly(deviceId: string, telemetry: Record<string, unknown>) {
    const temperature = Number(telemetry.temperature);
    if (Number.isFinite(temperature) && temperature > 90) {
      const { data: device } = await this.supabase.from('devices').select('organization_id').eq('id', deviceId).maybeSingle();
      if (device?.organization_id) return this.ingestEvent({
        organizationId: device.organization_id, deviceId, eventType: 'anomaly', severity: 'high',
        details: { type: 'high_temperature', value: temperature },
      });
    }
    return null;
  }

  static fingerprint(value: string) { return createHash('sha256').update(value).digest('hex'); }
}
