const WIDTH = 480;
const HEIGHT = 190;
const PADDING = { top: 16, right: 16, bottom: 12, left: 56 };

export default function PriceHistoryChart({ points }) {
  if (!points || points.length === 0) return null;

  const innerW = WIDTH - PADDING.left - PADDING.right;
  const innerH = HEIGHT - PADDING.top - PADDING.bottom;

  const prices = points.map((p) => p.price);
  let min = Math.min(...prices);
  let max = Math.max(...prices);
  if (min === max) {
    min -= 1;
    max += 1;
  } else {
    const pad = (max - min) * 0.15;
    min -= pad;
    max += pad;
  }

  const times = points.map((p) => new Date(p.t).getTime());
  const minT = times[0];
  const maxT = times[times.length - 1];

  const xFor = (t) => PADDING.left + (maxT === minT ? innerW / 2 : ((t - minT) / (maxT - minT)) * innerW);
  const yFor = (price) => PADDING.top + innerH - ((price - min) / (max - min)) * innerH;

  const coords = points.map((p, i) => ({ ...p, x: xFor(times[i]), y: yFor(p.price) }));
  const linePath = coords.map((c, i) => `${i === 0 ? 'M' : 'L'} ${c.x.toFixed(1)} ${c.y.toFixed(1)}`).join(' ');
  const floorY = (PADDING.top + innerH).toFixed(1);
  const areaPath = `${linePath} L ${coords[coords.length - 1].x.toFixed(1)} ${floorY} L ${coords[0].x.toFixed(1)} ${floorY} Z`;

  return (
    <svg viewBox={`0 0 ${WIDTH} ${HEIGHT}`} className="price-chart" role="img" aria-label="Price history over time">
      <line className="chart-axis" x1={PADDING.left} y1={PADDING.top} x2={PADDING.left} y2={PADDING.top + innerH} />
      <line className="chart-axis" x1={PADDING.left} y1={PADDING.top + innerH} x2={WIDTH - PADDING.right} y2={PADDING.top + innerH} />
      <text className="chart-axis-label" x={PADDING.left - 8} y={PADDING.top + 4} textAnchor="end">
        ${max.toFixed(2)}
      </text>
      <text className="chart-axis-label" x={PADDING.left - 8} y={PADDING.top + innerH} textAnchor="end">
        ${min.toFixed(2)}
      </text>
      <path d={areaPath} className="price-chart-area" />
      <path d={linePath} className="price-chart-line" fill="none" />
      {coords.map((c, i) => (
        <g key={i}>
          <circle cx={c.x} cy={c.y} r="3.5" className="price-chart-dot" />
          <title>{`${c.label}: $${c.price.toFixed(2)}`}</title>
        </g>
      ))}
    </svg>
  );
}
