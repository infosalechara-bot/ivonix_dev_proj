import { Body, Controller, Get, Param, Post } from '@nestjs/common';
import { SecurityService } from './security.service';
import { AnomalyDto, SecurityEventDto, ThreatIndicatorDto } from './dto/security-event.dto';

@Controller('security')
export class SecurityController {
  constructor(private readonly security: SecurityService) {}

  @Post('events') ingestEvent(@Body() dto: SecurityEventDto) { return this.security.ingestEvent(dto); }
  @Post('threat-indicators') addThreat(@Body() dto: ThreatIndicatorDto) { return this.security.addThreatIndicator(dto); }
  @Get('threat/:type/:indicator') lookup(@Param('type') type: string, @Param('indicator') indicator: string) { return this.security.lookupThreat(indicator, type); }
  @Get('scan/:hash') scanFile(@Param('hash') hash: string) { return this.security.scanFileHash(hash); }
  @Post('anomaly') anomaly(@Body() dto: AnomalyDto) { return this.security.detectAnomaly(dto.deviceId, dto.telemetry); }
}
