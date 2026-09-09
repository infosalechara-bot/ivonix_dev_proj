export function diagnose({ telemetry = {}, evidenceTypes = [], deviceDomain = 'unknown' } = {}) {
  let probableFault = 'No fault detected';
  let confidence = 0.5;
  const recommendedActions = [];

  const temperature = Number(telemetry.temperature);
  const vibration = Number(telemetry.vibration_rms);

  if (Number.isFinite(temperature) && temperature > 85) {
    probableFault = 'Overheating';
    confidence = 0.92;
    recommendedActions.push('Check cooling system', 'Verify airflow', 'Inspect fan');
  } else if (Number.isFinite(vibration) && vibration > 1.5) {
    probableFault = 'Bearing degradation';
    confidence = 0.85;
    recommendedActions.push('Stop machine', 'Inspect bearing');
  } else if (evidenceTypes.includes('audio')) {
    probableFault = 'Acoustic anomaly detected';
    confidence = 0.7;
    recommendedActions.push('Record additional audio samples', 'Check for loose components');
  }

  return {
    probable_fault: probableFault,
    confidence,
    recommended_actions: recommendedActions,
    do_not_disassemble: confidence < 0.7,
    evidence: {
      telemetry,
      evidence_types: evidenceTypes,
      device_domain,
    },
  };
}
