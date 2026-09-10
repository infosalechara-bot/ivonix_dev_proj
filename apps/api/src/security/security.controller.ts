import { Body, Controller, Get, Headers, Param, Post, UnauthorizedException } from '@nestjs/common';
import { SecurityService } from './security.service';
import { AnomalyDto, SecurityEventDto, ThreatIndicatorDto } from './dto/security-event.dto';

@Controller('security')
export class SecurityController {
  constructor(private readonly security: SecurityService) {}

  private async userId(authorization?: string) {
    if (!authorization?.startsWith('Bearer ')) throw new UnauthorizedException('Bearer token required');
    return this.security.authenticate(authorization.slice(7).trim());
  }

  @Get('health')
  health(@Headers('authorization') authorization: string | undefined) {
    return this.userId(authorization).then((userId) => ({ status: 'ok', authenticated: true, userId }));
  }

  @Post('events')
  ingestEvent(@Headers('authorization') authorization: string | undefined, @Body() dto: SecurityEventDto) {
    return this.userId(authorization).then((userId) => this.security.ingestEvent(dto, userId));
  }

  @Post('threat-indicators')
  addThreat(@Headers('authorization') authorization: string | undefined, @Body() dto: ThreatIndicatorDto) {
    return this.userId(authorization).then((userId) => this.security.addThreatIndicator(dto, userId));
  }

  @Get('threat/:type/:indicator')
  lookup(@Headers('authorization') authorization: string | undefined, @Param('type') type: string, @Param('indicator') indicator: string) {
    return this.userId(authorization).then((userId) => this.security.lookupThreat(indicator, type, userId));
  }

  @Get('scan/:hash')
  scanFile(@Headers('authorization') authorization: string | undefined, @Param('hash') hash: string) {
    return this.userId(authorization).then(() => this.security.scanFileHash(hash));
  }

  @Post('anomaly')
  anomaly(@Headers('authorization') authorization: string | undefined, @Body() dto: AnomalyDto) {
    return this.userId(authorization).then((userId) => this.security.detectAnomaly(dto.deviceId, dto.telemetry, userId));
  }
}
