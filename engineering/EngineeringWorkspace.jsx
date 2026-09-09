import React, { useMemo, useState } from 'react';
import { diagnose } from './diagnostic-engine.js';

export default function EngineeringWorkspace({ deviceId = 'demo-machine' }) {
  const [temperature, setTemperature] = useState(90);
  const [vibration, setVibration] = useState(1.2);
  const [result, setResult] = useState(null);
  const [loading, setLoading] = useState(false);

  const telemetry = useMemo(() => ({
    temperature: Number(temperature),
    vibration_rms: Number(vibration),
  }), [temperature, vibration]);

  const runDiagnosis = async () => {
    setLoading(true);
    await new Promise((resolve) => setTimeout(resolve, 350));
    setResult(diagnose({ telemetry }));
    setLoading(false);
  };

  return (
    <section className="pulse-engineering" aria-label="PULSE Engineering">
      <div className="engineering-header">
        <div>
          <span className="eyebrow">PULSE ENGINEERING</span>
          <h2>Machine diagnostic workspace</h2>
          <p>Machine: {deviceId}</p>
        </div>
        <span className="status-dot">ENGINE READY</span>
      </div>

      <div className="telemetry-grid">
        <label>Temperature °C<input type="number" value={temperature} onChange={(e) => setTemperature(e.target.value)} /></label>
        <label>Vibration RMS<input type="number" step="0.1" value={vibration} onChange={(e) => setVibration(e.target.value)} /></label>
      </div>

      <button className="diagnose-button" onClick={runDiagnosis} disabled={loading}>
        {loading ? 'Diagnosing…' : 'Run Diagnosis'}
      </button>

      {result && (
        <div className="diagnosis-card">
          <div className="diagnosis-title">
            <span>Probable fault</span>
            <strong>{result.probable_fault}</strong>
          </div>
          <div className="confidence">Confidence {(result.confidence * 100).toFixed(1)}%</div>
          {result.do_not_disassemble && <div className="safety-warning">⚠ Do not disassemble — insufficient evidence.</div>}
          <h3>Recommended actions</h3>
          <ul>{result.recommended_actions.map((action) => <li key={action}>{action}</li>)}</ul>
        </div>
      )}
    </section>
  );
}
