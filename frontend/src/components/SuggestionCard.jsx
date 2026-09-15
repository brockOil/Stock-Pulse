import Badge from './Badge.jsx';

export default function SuggestionCard({ suggestion, type, onAccept, onReject, busy }) {
  const isPricing = type === 'pricing';
  return (
    <div className="card suggestion-card">
      <div className="suggestion-head">
        <div>
          <div className="suggestion-product">
            {suggestion.productName} <span className="mono muted">({suggestion.productSku})</span>
          </div>
          <div className="suggestion-badges">
            <Badge value={suggestion.triggerReason} />
            <Badge value={suggestion.generatedBy} />
            <span className="suggestion-type">{isPricing ? 'Pricing' : 'Reorder'}</span>
          </div>
        </div>
        <div className="suggestion-confidence">{Math.round(suggestion.confidence * 100)}% confidence</div>
      </div>

      {isPricing ? (
        <div className="suggestion-value">
          ${Number(suggestion.currentPriceAtSuggestion).toFixed(2)} <span className="arrow">-&gt;</span>{' '}
          <strong>${Number(suggestion.recommendedPrice).toFixed(2)}</strong>
          <span className={`direction direction-${suggestion.changeDirection.toLowerCase()}`}>{suggestion.changeDirection}</span>
        </div>
      ) : (
        <div className="suggestion-value">
          Reorder <strong>{suggestion.recommendedQuantity}</strong> units · lead time {suggestion.suggestedLeadTimeDays}d
        </div>
      )}

      <p className="suggestion-reasoning">{suggestion.reasoning}</p>

      <div className="suggestion-actions">
        <button type="button" className="btn btn-accept" disabled={busy} onClick={() => onAccept(suggestion.id)}>
          Accept
        </button>
        <button type="button" className="btn btn-reject" disabled={busy} onClick={() => onReject(suggestion.id)}>
          Reject
        </button>
      </div>
    </div>
  );
}
