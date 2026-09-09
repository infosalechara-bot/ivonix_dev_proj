import { Module } from '@nestjs/common';
import { ConfigModule } from '@nestjs/config';
import { SecurityModule } from './security/security.module';

@Module({
  imports: [ConfigModule.forRoot({ isGlobal: true }), SecurityModule],
})
export class AppModule {}
