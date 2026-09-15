export default function StrategySwitcher({ config, onChange, disabled }) {
  if (!config) return null;
  return (
    <div className="strategy-switcher">
      <StrategyToggle label="Pricing engine" type="PRICING" mode={config.pricingStrategy} onChange={onChange} disabled={disabled} />
      <StrategyToggle label="Reorder engine" type="REORDER" mode={config.reorderStrategy} onChange={onChange} disabled={disabled} />
    </div>
  );
}

function StrategyToggle({ label, type, mode, onChange, disabled }) {
  return (
    <div className="strategy-toggle">
      <span className="strategy-toggle-label">{label}</span>
      <div className="segmented" role="group" aria-label={`${label} strategy`}>
        {['RULE_BASED', 'AI'].map((m) => (
          <button
            key={m}
            type="button"
            className={`segmented-btn ${mode === m ? 'active' : ''}`}
            onClick={() => onChange(type, m)}
            disabled={disabled || mode === m}
          >
            {m === 'RULE_BASED' ? 'Rule-based' : 'AI'}
          </button>
        ))}
      </div>
    </div>
  );
}
