import { useCallback, useEffect, useRef, useState } from 'react';
import { api, streamPricingSuggestion } from './api.js';
import ProductList from './components/ProductList.jsx';
import SuggestionsPanel from './components/SuggestionsPanel.jsx';
import CreateProductForm from './components/CreateProductForm.jsx';
import StrategySwitcher from './components/StrategySwitcher.jsx';
import StreamPanel from './components/StreamPanel.jsx';

const POLL_INTERVAL_MS = 4000;
const CATEGORIES = ['', 'ELECTRONICS', 'APPAREL', 'HOME'];
const STATUSES = ['', 'ACTIVE', 'PRICE_REVIEW_PENDING', 'OUT_OF_STOCK'];

export default function App() {
  const [products, setProducts] = useState([]);
  const [pricingSuggestions, setPricingSuggestions] = useState([]);
  const [reorderSuggestions, setReorderSuggestions] = useState([]);
  const [strategyConfig, setStrategyConfig] = useState(null);

  const [statusFilter, setStatusFilter] = useState('');
  const [categoryFilter, setCategoryFilter] = useState('');

  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [busyId, setBusyId] = useState(null);
  const [lastRefreshed, setLastRefreshed] = useState(null);

  const [streamState, setStreamState] = useState(null);
  const streamAbortRef = useRef(null);

  const refreshAll = useCallback(async () => {
    try {
      const [productList, pending, pendingReorders, config] = await Promise.all([
        api.listProducts({ status: statusFilter, category: categoryFilter }),
        api.listPricingSuggestions({ status: 'PENDING' }),
        api.listReorderSuggestions({ status: 'PENDING' }),
        api.getStrategyConfig(),
      ]);
      setProducts(productList);
      setPricingSuggestions(pending);
      setReorderSuggestions(pendingReorders);
      setStrategyConfig(config);
      setLastRefreshed(new Date());
      setError(null);
    } catch (e) {
      setError(e.message);
    } finally {
      setLoading(false);
    }
  }, [statusFilter, categoryFilter]);

  useEffect(() => {
    refreshAll();
    const interval = setInterval(refreshAll, POLL_INTERVAL_MS);
    return () => clearInterval(interval);
  }, [refreshAll]);

  useEffect(() => () => streamAbortRef.current?.abort(), []);

  async function runAction(id, fn) {
    setBusyId(id);
    try {
      await fn();
      await refreshAll();
    } catch (e) {
      setError(e.message);
    } finally {
      setBusyId(null);
    }
  }

  const handleCreateProduct = async (payload) => {
    await api.createProduct(payload);
    await refreshAll();
  };

  const handleSimulateSale = (id) => runAction(id, () => api.placeOrder(id, 1));
  const handleUpdateStock = (id, stockLevel) => runAction(id, () => api.updateStock(id, stockLevel));
  const handleSuggestPricing = (id) => runAction(id, () => api.suggestPricing(id));
  const handleSuggestReorder = (id) => runAction(id, () => api.suggestReorder(id));

  const handleAcceptPricing = (id) => runAction(id, () => api.decidePricingSuggestion(id, 'ACCEPTED'));
  const handleRejectPricing = (id) => runAction(id, () => api.decidePricingSuggestion(id, 'REJECTED'));
  const handleAcceptReorder = (id) => runAction(id, () => api.decideReorderSuggestion(id, 'ACCEPTED'));
  const handleRejectReorder = (id) => runAction(id, () => api.decideReorderSuggestion(id, 'REJECTED'));

  const handleStrategyChange = async (suggestionType, mode) => {
    try {
      const config = await api.setStrategyMode(suggestionType, mode);
      setStrategyConfig(config);
    } catch (e) {
      setError(e.message);
    }
  };

  const handleStream = (product) => {
    streamAbortRef.current?.abort();
    const controller = new AbortController();
    streamAbortRef.current = controller;

    setStreamState({ productId: product.id, productName: product.name, tokens: '', status: 'streaming', error: null });

    streamPricingSuggestion(product.id, {
      signal: controller.signal,
      onToken: (token) => setStreamState((s) => (s && s.productId === product.id ? { ...s, tokens: s.tokens + token } : s)),
      onSuggestion: () => {
        setStreamState((s) => (s && s.productId === product.id ? { ...s, status: 'done' } : s));
        refreshAll();
      },
      onError: (e) => setStreamState((s) => (s && s.productId === product.id ? { ...s, status: 'error', error: e.message } : s)),
    });
  };

  return (
    <div className="app">
      <header className="app-header">
        <div>
          <p className="header-kicker">StockPulse · Merchandising console</p>
          <div className="brand-row">
            <div className="logo-mark" aria-hidden="true">
              <span>SP</span>
            </div>
            <div>
              <h1>
                <em>AI</em> Inventory &amp; Pricing
              </h1>
              <p className="tagline">Signals in, recommendations out - you keep the checkpoint.</p>
            </div>
          </div>
        </div>
        <StrategySwitcher config={strategyConfig} onChange={handleStrategyChange} />
      </header>

      {error && (
        <div className="banner banner-error">
          <span>{error}</span>
          <button type="button" className="btn btn-tiny btn-ghost" onClick={() => setError(null)}>
            Dismiss
          </button>
        </div>
      )}

      <div className="toolbar">
        <CreateProductForm onCreate={handleCreateProduct} />

        <div className="filters">
          <label>
            Status
            <select value={statusFilter} onChange={(e) => setStatusFilter(e.target.value)}>
              {STATUSES.map((s) => (
                <option key={s || 'all'} value={s}>
                  {s === '' ? 'All statuses' : s.replaceAll('_', ' ')}
                </option>
              ))}
            </select>
          </label>
          <label>
            Category
            <select value={categoryFilter} onChange={(e) => setCategoryFilter(e.target.value)}>
              {CATEGORIES.map((c) => (
                <option key={c || 'all'} value={c}>
                  {c === '' ? 'All categories' : c}
                </option>
              ))}
            </select>
          </label>
        </div>

        <div className="refresh-status">
          <span className={`live-dot${loading ? ' loading' : ''}`} aria-hidden="true" />
          {loading ? 'Syncing…' : lastRefreshed ? `Updated ${lastRefreshed.toLocaleTimeString()}` : null}
        </div>
      </div>

      {streamState && <StreamPanel state={streamState} onClose={() => setStreamState(null)} />}

      <main className="layout">
        <section className="layout-main">
          <ProductList
            products={products}
            busyId={busyId}
            onSimulateSale={handleSimulateSale}
            onUpdateStock={handleUpdateStock}
            onSuggestPricing={handleSuggestPricing}
            onSuggestReorder={handleSuggestReorder}
            onStream={handleStream}
          />
        </section>
        <section className="layout-side">
          <SuggestionsPanel
            pricingSuggestions={pricingSuggestions}
            reorderSuggestions={reorderSuggestions}
            busyId={busyId}
            onAcceptPricing={handleAcceptPricing}
            onRejectPricing={handleRejectPricing}
            onAcceptReorder={handleAcceptReorder}
            onRejectReorder={handleRejectReorder}
          />
        </section>
      </main>
    </div>
  );
}
