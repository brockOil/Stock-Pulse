import ProductRow from './ProductRow.jsx';

export default function ProductList({ products, busyId, onSimulateSale, onUpdateStock, onSuggestPricing, onSuggestReorder, onStream }) {
  return (
    <div>
      <h3 className="panel-title">Catalog ({products.length})</h3>
      <div className="card table-card">
        <div className="table-scroll">
          <table className="product-table">
            <thead>
              <tr>
                <th>SKU</th>
                <th>Name</th>
                <th>Category</th>
                <th>Price</th>
                <th>Stock</th>
                <th>Threshold</th>
                <th>Velocity</th>
                <th>Status</th>
                <th>Actions</th>
              </tr>
            </thead>
            <tbody>
              {products.map((p) => (
                <ProductRow
                  key={p.id}
                  product={p}
                  busy={busyId === p.id}
                  onSimulateSale={onSimulateSale}
                  onUpdateStock={onUpdateStock}
                  onSuggestPricing={onSuggestPricing}
                  onSuggestReorder={onSuggestReorder}
                  onStream={onStream}
                />
              ))}
              {products.length === 0 && (
                <tr>
                  <td colSpan={9} className="empty-row">
                    No products match the current filters.
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        </div>
      </div>
    </div>
  );
}
