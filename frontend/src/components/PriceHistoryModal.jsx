import { useEffect, useState } from 'react';
import { api } from '../api.js';
import PriceHistoryChart from './PriceHistoryChart.jsx';

/**
 * Reconstructs price history from real ACCEPTED pricing suggestions (currentPriceAtSuggestion
 * -> recommendedPrice at decidedAt) rather than a separate, easily-out-of-sync history table -
 * the suggestion audit trail already IS the price history.
 */
export default function PriceHistoryModal({ product, onClose }) {
  const [state, setState] = useState({ loading: true, error: null, points: [] });

  useEffect(() => {
    let cancelled = false;
    setState({ loading: true, error: null, points: [] });

    api
      .listPricingSuggestions({ productId: product.id, status: 'ACCEPTED' })
      .then((suggestions) => {
        if (cancelled) return;
        const chronological = [...suggestions].sort((a, b) => new Date(a.decidedAt) - new Date(b.decidedAt));
        const points = [];
        if (chronological.length > 0) {
          points.push({
            t: chronological[0].createdAt,
            price: Number(chronological[0].currentPriceAtSuggestion),
            label: 'Initial',
          });
          for (const s of chronological) {
            points.push({ t: s.decidedAt, price: Number(s.recommendedPrice), label: new Date(s.decidedAt).toLocaleDateString() });
          }
        }
        points.push({ t: new Date().toISOString(), price: Number(product.currentPrice), label: 'Now' });
        setState({ loading: false, error: null, points });
      })
      .catch((e) => {
        if (!cancelled) setState({ loading: false, error: e.message, points: [] });
      });

    return () => {
      cancelled = true;
    };
  }, [product.id, product.currentPrice]);

  return (
    <div className="modal-overlay" onClick={onClose}>
      <div className="modal-card" onClick={(e) => e.stopPropagation()}>
        <div className="modal-head">
          <div>
            <strong>Price history</strong> <span className="mono muted">· {product.name}</span>
          </div>
          <button type="button" className="btn btn-tiny btn-ghost" onClick={onClose}>
            Close
          </button>
        </div>

        {state.loading && <div className="modal-loading">Loading…</div>}
        {state.error && <div className="form-error">{state.error}</div>}
        {!state.loading && !state.error && (
          <>
            <PriceHistoryChart points={state.points} />
            {state.points.length <= 1 && (
              <p className="muted history-empty">
                No accepted price changes yet - this is the price the product was created with.
              </p>
            )}
          </>
        )}
      </div>
    </div>
  );
}
