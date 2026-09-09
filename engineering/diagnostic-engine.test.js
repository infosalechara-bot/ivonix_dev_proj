import { diagnose } from './diagnostic-engine.js';

const overheating = diagnose({ telemetry: { temperature: 90, vibration_rms: 1.2 } });
if (overheating.probable_fault !== 'Overheating' || overheating.confidence !== 0.92) {
  throw new Error('Overheating rule failed');
}

const bearing = diagnose({ telemetry: { temperature: 60, vibration_rms: 1.8 } });
if (bearing.probable_fault !== 'Bearing degradation' || bearing.confidence !== 0.85) {
  throw new Error('Bearing rule failed');
}

const audio = diagnose({ evidenceTypes: ['audio'] });
if (audio.probable_fault !== 'Acoustic anomaly detected' || audio.confidence !== 0.7) {
  throw new Error('Audio rule failed');
}

console.log('PULSE Engineering diagnostic tests passed');
