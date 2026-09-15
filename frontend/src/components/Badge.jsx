const VARIANTS = {
  // Product status
  ACTIVE: 'badge badge-green',
  PRICE_REVIEW_PENDING: 'badge badge-amber',
  OUT_OF_STOCK: 'badge badge-red',
  // Trigger reason
  INVENTORY_LOW: 'badge badge-amber',
  DEMAND_SPIKE: 'badge badge-purple',
  MANUAL: 'badge badge-gray',
  INITIAL: 'badge badge-gray',
  // Recommendation source
  AI: 'badge badge-blue',
  RULE_BASED: 'badge badge-gray',
  // Suggestion status
  PENDING: 'badge badge-amber',
  ACCEPTED: 'badge badge-green',
  REJECTED: 'badge badge-red',
};

export default function Badge({ value, label }) {
  const className = VARIANTS[value] || 'badge badge-gray';
  return <span className={className}>{label || formatLabel(value)}</span>;
}

function formatLabel(value) {
  if (!value) return '';
  return value.replaceAll('_', ' ');
}
