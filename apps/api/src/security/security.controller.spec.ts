import { UnauthorizedException } from '@nestjs/common';
import { SecurityController } from './security.controller';

describe('SecurityController authentication boundary', () => {
  const security = {
    authenticate: jest.fn().mockResolvedValue('user-1'),
    ingestEvent: jest.fn().mockResolvedValue({ ok: true }),
    addThreatIndicator: jest.fn().mockResolvedValue({ ok: true }),
    lookupThreat: jest.fn().mockResolvedValue(null),
    scanFileHash: jest.fn().mockResolvedValue({ malicious: false }),
    detectAnomaly: jest.fn().mockResolvedValue(null),
  } as any;

  beforeEach(() => jest.clearAllMocks());

  it('rejects requests without a bearer token', async () => {
    const controller = new SecurityController(security);
    await expect(controller.lookup(undefined, 'ip', '127.0.0.1')).rejects.toBeInstanceOf(UnauthorizedException);
    expect(security.authenticate).not.toHaveBeenCalled();
  });

  it('passes only the bearer token to authentication', async () => {
    const controller = new SecurityController(security);
    await controller.lookup('Bearer signed-user-token', 'ip', '127.0.0.1');
    expect(security.authenticate).toHaveBeenCalledWith('signed-user-token');
    expect(security.lookupThreat).toHaveBeenCalledWith('127.0.0.1', 'ip', 'user-1');
  });
});
