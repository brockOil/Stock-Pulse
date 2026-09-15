const BASE_URL = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080/api';

export class ApiError extends Error {
  constructor(message, status, details) {
    super(message);
    this.name = 'ApiError';
    this.status = status;
    this.details = details;
  }
}

function toQuery(params) {
  const entries = Object.entries(params).filter(([, v]) => v !== undefined && v !== null && v !== '');
  if (entries.length === 0) return '';
  return '?' + new URLSearchParams(entries).toString();
}

async function request(path, options = {}) {
  let res;
  try {
    res = await fetch(`${BASE_URL}${path}`, {
      headers: { 'Content-Type': 'application/json', ...(options.headers || {}) },
      ...options,
    });
  } catch {
    throw new ApiError('Could not reach the StockPulse API. Is the backend running on port 8080?', 0);
  }

  if (!res.ok) {
    let body = null;
    try {
      body = await res.json();
    } catch {
      // no JSON body to read
    }
    throw new ApiError(body?.message || `Request failed (${res.status})`, res.status, body?.details);
  }
  if (res.status === 204) return null;
  const text = await res.text();
  return text ? JSON.parse(text) : null;
}

export const api = {
  listProducts: (params = {}) => request(`/products${toQuery(params)}`),
  getProduct: (id) => request(`/products/${id}`),
  createProduct: (payload) => request('/products', { method: 'POST', body: JSON.stringify(payload) }),
  updateStock: (id, stockLevel) =>
    request(`/products/${id}/stock`, { method: 'PATCH', body: JSON.stringify({ stockLevel }) }),
  placeOrder: (id, quantity = 1) =>
    request(`/products/${id}/orders`, { method: 'POST', body: JSON.stringify({ quantity }) }),
  suggestPricing: (id) => request(`/products/${id}/suggest-pricing`, { method: 'POST' }),
  suggestReorder: (id) => request(`/products/${id}/suggest-reorder`, { method: 'POST' }),

  listPricingSuggestions: (params = {}) => request(`/pricing-suggestions${toQuery(params)}`),
  listReorderSuggestions: (params = {}) => request(`/reorder-suggestions${toQuery(params)}`),
  decidePricingSuggestion: (id, status) =>
    request(`/pricing-suggestions/${id}`, { method: 'PATCH', body: JSON.stringify({ status }) }),
  decideReorderSuggestion: (id, status) =>
    request(`/reorder-suggestions/${id}`, { method: 'PATCH', body: JSON.stringify({ status }) }),

  getStrategyConfig: () => request('/config/strategy'),
  setStrategyMode: (suggestionType, mode) =>
    request('/config/strategy', { method: 'PATCH', body: JSON.stringify({ suggestionType, mode }) }),
};

/**
 * Bonus SSE endpoint. The backend streams over POST, so this reads the response body with a
 * plain fetch + ReadableStream reader rather than the browser's EventSource API (which can
 * only issue GET requests).
 */
export async function streamPricingSuggestion(productId, { onToken, onSuggestion, onError, signal } = {}) {
  let res;
  try {
    res = await fetch(`${BASE_URL}/products/${productId}/suggest-pricing/stream`, {
      method: 'POST',
      headers: { Accept: 'text/event-stream' },
      signal,
    });
  } catch (e) {
    onError?.(new ApiError('Could not reach the streaming endpoint.', 0));
    return;
  }
  if (!res.ok || !res.body) {
    onError?.(new ApiError(`Stream request failed (${res.status})`, res.status));
    return;
  }

  const reader = res.body.getReader();
  const decoder = new TextDecoder();
  let buffer = '';

  while (true) {
    const { value, done } = await reader.read();
    if (done) break;
    buffer += decoder.decode(value, { stream: true });

    const events = buffer.split('\n\n');
    buffer = events.pop() ?? '';

    for (const raw of events) {
      const lines = raw.split('\n');
      let eventName = 'message';
      let data = '';
      for (const line of lines) {
        if (line.startsWith('event:')) eventName = line.slice(6).trim();
        else if (line.startsWith('data:')) data += line.slice(5).trim();
      }
      if (!data) continue;
      if (eventName === 'token') onToken?.(data);
      else if (eventName === 'suggestion') onSuggestion?.(JSON.parse(data));
      else if (eventName === 'error') onError?.(new ApiError(data, 0));
    }
  }
}
