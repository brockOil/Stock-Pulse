import { useState } from 'react';

const CATEGORIES = ['ELECTRONICS', 'APPAREL', 'HOME'];

const EMPTY_FORM = {
  sku: '',
  name: '',
  category: 'ELECTRONICS',
  currentPrice: '',
  stockLevel: '',
  reorderThreshold: '',
  demandVelocity: '',
  costPrice: '',
};

export default function CreateProductForm({ onCreate }) {
  const [open, setOpen] = useState(false);
  const [form, setForm] = useState(EMPTY_FORM);
  const [error, setError] = useState(null);
  const [submitting, setSubmitting] = useState(false);

  function update(field, value) {
    setForm((f) => ({ ...f, [field]: value }));
  }

  async function handleSubmit(e) {
    e.preventDefault();
    setError(null);

    if (!form.sku.trim() || !form.name.trim()) {
      setError('SKU and name are required.');
      return;
    }
    const payload = {
      sku: form.sku.trim(),
      name: form.name.trim(),
      category: form.category,
      currentPrice: Number(form.currentPrice),
      stockLevel: Number(form.stockLevel),
      reorderThreshold: Number(form.reorderThreshold),
      demandVelocity: form.demandVelocity === '' ? 0 : Number(form.demandVelocity),
      costPrice: form.costPrice === '' ? null : Number(form.costPrice),
    };
    if (!(payload.currentPrice > 0)) {
      setError('Price must be a positive number.');
      return;
    }
    if (!(payload.stockLevel >= 0) || !(payload.reorderThreshold >= 0)) {
      setError('Stock level and reorder threshold must be zero or greater.');
      return;
    }
    if (payload.costPrice !== null && !(payload.costPrice >= 0)) {
      setError('Cost price must be zero or greater.');
      return;
    }

    setSubmitting(true);
    try {
      await onCreate(payload);
      setForm(EMPTY_FORM);
      setOpen(false);
    } catch (err) {
      setError(err.message);
    } finally {
      setSubmitting(false);
    }
  }

  if (!open) {
    return (
      <button type="button" className="btn btn-primary" onClick={() => setOpen(true)}>
        + New product
      </button>
    );
  }

  return (
    <form className="card create-product-form" onSubmit={handleSubmit}>
      <div className="form-header">
        <h3>New product</h3>
        <button type="button" className="btn btn-ghost" onClick={() => setOpen(false)}>
          Cancel
        </button>
      </div>
      {error && <div className="form-error">{error}</div>}
      <div className="form-grid">
        <label>
          SKU
          <input value={form.sku} onChange={(e) => update('sku', e.target.value)} maxLength={64} required />
        </label>
        <label>
          Name
          <input value={form.name} onChange={(e) => update('name', e.target.value)} maxLength={200} required />
        </label>
        <label>
          Category
          <select value={form.category} onChange={(e) => update('category', e.target.value)}>
            {CATEGORIES.map((c) => (
              <option key={c} value={c}>
                {c}
              </option>
            ))}
          </select>
        </label>
        <label>
          Price ($)
          <input
            type="number"
            min="0.01"
            step="0.01"
            value={form.currentPrice}
            onChange={(e) => update('currentPrice', e.target.value)}
            required
          />
        </label>
        <label>
          Stock level
          <input
            type="number"
            min="0"
            step="1"
            value={form.stockLevel}
            onChange={(e) => update('stockLevel', e.target.value)}
            required
          />
        </label>
        <label>
          Reorder threshold
          <input
            type="number"
            min="0"
            step="1"
            value={form.reorderThreshold}
            onChange={(e) => update('reorderThreshold', e.target.value)}
            required
          />
        </label>
        <label>
          Demand velocity
          <input
            type="number"
            min="0"
            step="1"
            placeholder="0"
            value={form.demandVelocity}
            onChange={(e) => update('demandVelocity', e.target.value)}
          />
        </label>
        <label>
          Cost price ($, optional)
          <input
            type="number"
            min="0"
            step="0.01"
            placeholder="for margin display"
            value={form.costPrice}
            onChange={(e) => update('costPrice', e.target.value)}
          />
        </label>
      </div>
      <button className="btn btn-primary" type="submit" disabled={submitting}>
        {submitting ? 'Creating…' : 'Create product'}
      </button>
    </form>
  );
}
