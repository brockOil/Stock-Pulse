import SuggestionCard from './SuggestionCard.jsx';

export default function SuggestionsPanel({ pricingSuggestions, reorderSuggestions, busyId, onAcceptPricing, onRejectPricing, onAcceptReorder, onRejectReorder }) {
  const items = [
    ...pricingSuggestions.map((s) => ({ type: 'pricing', suggestion: s })),
    ...reorderSuggestions.map((s) => ({ type: 'reorder', suggestion: s })),
  ].sort((a, b) => new Date(b.suggestion.createdAt) - new Date(a.suggestion.createdAt));

  return (
    <div className="suggestions-panel">
      <h3 className="panel-title">Pending suggestions ({items.length})</h3>
      {items.length === 0 && <div className="card empty-state">No pending suggestions right now.</div>}
      {items.map(({ type, suggestion }) =>
        type === 'pricing' ? (
          <SuggestionCard
            key={`p-${suggestion.id}`}
            suggestion={suggestion}
            type="pricing"
            busy={busyId === suggestion.id}
            onAccept={onAcceptPricing}
            onReject={onRejectPricing}
          />
        ) : (
          <SuggestionCard
            key={`r-${suggestion.id}`}
            suggestion={suggestion}
            type="reorder"
            busy={busyId === suggestion.id}
            onAccept={onAcceptReorder}
            onReject={onRejectReorder}
          />
        )
      )}
    </div>
  );
}
