import { Injectable, Logger } from '@nestjs/common';
import { Cron, CronExpression } from '@nestjs/schedule';
import { ConfigService } from '@nestjs/config';
import { SupabaseClient } from '@supabase/supabase-js';
import axios from 'axios';

@Injectable()
export class ThreatIntelFetcher {
  private readonly logger = new Logger(ThreatIntelFetcher.name);
  constructor(private readonly supabase: SupabaseClient, private readonly config: ConfigService) {}

  @Cron(CronExpression.EVERY_HOUR)
  async fetchMisp() {
    const url = this.config.get<string>('MISP_URL');
    const key = this.config.get<string>('MISP_API_KEY');
    if (!url || !key) return;
    try {
      const { data } = await axios.get(`${url.replace(/\/$/, '')}/events/index`, {
        headers: { Authorization: key, Accept: 'application/json' }, timeout: 15000,
      });
      const events = Array.isArray(data) ? data : [];
      for (const event of events) {
        for (const attr of event?.Event?.Attribute ?? []) {
          const type = this.mapType(attr.type);
          if (!type || !attr.value) continue;
          await this.supabase.from('threat_indicators').upsert({
            indicator: attr.value, type, source: 'misp', active: true,
            last_seen: new Date().toISOString(),
          }, { onConflict: 'indicator,type' });
        }
      }
    } catch (error) {
      this.logger.warn(`MISP fetch failed: ${error instanceof Error ? error.message : 'unknown error'}`);
    }
  }

  private mapType(type: string): 'ip' | 'domain' | 'hash' | 'url' | null {
    if (type === 'ip-src' || type === 'ip-dst') return 'ip';
    if (type === 'domain') return 'domain';
    if (['md5', 'sha1', 'sha256'].includes(type)) return 'hash';
    if (type === 'url') return 'url';
    return null;
  }
}
