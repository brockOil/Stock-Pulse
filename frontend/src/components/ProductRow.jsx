import { useState } from 'react';
import Badge from './Badge.jsx';

export default function ProductRow({ product, busy, onSimulateSale, onUpdateStock, onSuggestPricing, onSuggestReorder, onStream }) {
  const [stockDraft, setStockDraft] = useState(String(product.stockLevel));
  const [editingStock, setEditingStock] = useState(false);

  function submitStock() {
    const value = Number(stockDraft);
    if (Number.isInteger(value) && value >= 0) {
      onUpdateStock(product.id, value);
    }
    setEditingStock(false);
  }

  return (
    <tr className={product.stockLevel === 0 ? 'row-out-of-stock' : undefined}>
      <td className="mono">{product.sku}</td>
      <td>{product.name}</td>
      <td>{product.category}</td>
      <td className="mono">${Number(product.currentPrice).toFixed(2)}</td>
      <td className="mono">
        {editingStock ? (
          <span className="inline-edit">
            <input
              type="number"
              min="0"
              step="1"
              value={stockDraft}
              autoFocus
              onChange={(e) => setStockDraft(e.target.value)}
              onKeyDown={(e) => e.key === 'Enter' && submitStock()}
            />
            <button type="button" className="btn btn-tiny" onClick={submitStock}>
              Save
            </button>
            <button type="button" className="btn btn-tiny btn-ghost" onClick={() => setEditingStock(false)}>
              x
            </button>
          </span>
        ) : (
          <button
            type="button"
            className="stock-value"
            title="Click to set an absolute stock level"
            onClick={() => {
              setStockDraft(String(product.stockLevel));
              setEditingStock(true);
            }}
          >
            {product.stockLevel}
          </button>
        )}
      </td>
      <td className="mono">{product.reorderThreshold}</td>
      <td className="mono">{product.demandVelocity}/24h</td>
      <td>
        <Badge value={product.status} />
      </td>
      <td className="row-actions">
        <button type="button" className="btn btn-tiny" disabled={busy || product.stockLevel === 0} onClick={() => onSimulateSale(product.id)}>
          Simulate sale
        </button>
        <button type="button" className="btn btn-tiny btn-ghost" disabled={busy} onClick={() => onSuggestPricing(product.id)}>
          Suggest price
        </button>
        <button type="button" className="btn btn-tiny btn-ghost" disabled={busy} onClick={() => onSuggestReorder(product.id)}>
          Suggest reorder
        </button>
        <button type="button" className="btn btn-tiny btn-ghost" disabled={busy} onClick={() => onStream(product)}>
          Stream AI
        </button>
      </td>
    </tr>
  );
}
