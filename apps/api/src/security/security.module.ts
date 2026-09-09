import { Module } from '@nestjs/common';
import { ConfigModule, ConfigService } from '@nestjs/config';
import { ScheduleModule } from '@nestjs/schedule';
import { createClient } from '@supabase/supabase-js';
import { SecurityController } from './security.controller';
import { SecurityService } from './security.service';
import { ThreatIntelFetcher } from './threat-intel.fetcher';

@Module({
  imports: [ConfigModule, ScheduleModule.forRoot()],
  controllers: [SecurityController],
  providers: [
    {
      provide: 'SUPABASE_CLIENT',
      inject: [ConfigService],
      useFactory: (config: ConfigService) => createClient(
        config.getOrThrow<string>('SUPABASE_URL'),
        config.getOrThrow<string>('SUPABASE_SERVICE_ROLE_KEY'),
        { auth: { persistSession: false, autoRefreshToken: false } },
      ),
    },
    SecurityService,
    ThreatIntelFetcher,
  ],
  exports: [SecurityService],
})
export class SecurityModule {}
