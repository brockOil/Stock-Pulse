function zoneFor(product) {
  if (product.stockLevel === 0) return 'critical';
  const ratio = product.reorderThreshold > 0 ? product.stockLevel / product.reorderThreshold : 2;
  if (ratio < 1) return 'low';
  if (ratio < 1.5) return 'watch';
  return 'healthy';
}

export default function StockHeatmap({ products }) {
  if (products.length === 0) return null;

  const byCategory = products.reduce((acc, p) => {
    (acc[p.category] ??= []).push(p);
    return acc;
  }, {});

  return (
    <div className="card heatmap-card">
      <div className="heatmap-header">
        <h3 className="panel-title">Stock heatmap</h3>
        <div className="heatmap-legend">
          <span>
            <i className="legend-dot legend-critical" /> Out of stock
          </span>
          <span>
            <i className="legend-dot legend-low" /> Below threshold
          </span>
          <span>
            <i className="legend-dot legend-watch" /> Near threshold
          </span>
          <span>
            <i className="legend-dot legend-healthy" /> Healthy
          </span>
        </div>
      </div>

      {Object.entries(byCategory).map(([category, items]) => (
        <div key={category} className="heatmap-row">
          <span className="heatmap-row-label">{category}</span>
          <div className="heatmap-grid">
            {items.map((p) => (
              <div
                key={p.id}
                className={`heat-tile heat-${zoneFor(p)}`}
                title={`${p.name} (${p.sku})\nStock: ${p.stockLevel} · Threshold: ${p.reorderThreshold} · Velocity: ${p.demandVelocity}/24h`}
              >
                <span className="heat-stock">{p.stockLevel}</span>
                <span className="heat-name">{p.name}</span>
              </div>
            ))}
          </div>
        </div>
      ))}
    </div>
  );
}
