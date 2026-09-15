export default function StreamPanel({ state, onClose }) {
  if (!state) return null;
  const { productName, tokens, status, error } = state;

  return (
    <div className="card stream-panel">
      <div className="stream-head">
        <div>
          <strong>Live AI reasoning</strong> <span className="mono muted">· {productName}</span>
        </div>
        <button type="button" className="btn btn-tiny btn-ghost" onClick={onClose}>
          Close
        </button>
      </div>
      <div className="stream-body">
        {tokens ? <span>{tokens}</span> : <span className="muted">Waiting for the model…</span>}
        {status === 'streaming' && <span className="stream-cursor" aria-hidden="true" />}
      </div>
      {status === 'error' && <div className="form-error">{error}</div>}
      {status === 'done' && <div className="stream-done">Suggestion generated - see the pending list below.</div>}
    </div>
  );
}
