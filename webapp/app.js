'use strict';

// Renders a redstonelogics.graph/1 document.
//
// Geometry comes from the file, computed by DiagramLayout in Java, so this view
// cannot disagree with the exported SVG or PNG. That is the whole reason the
// exporter carries boxes and wire points instead of just a node/edge list.

const SVG_NS = 'http://www.w3.org/2000/svg';
const view = document.getElementById('view');
const stage = document.getElementById('stage');
const side = document.getElementById('side');
const drop = document.getElementById('drop');
const summary = document.getElementById('summary');
const hotButton = document.getElementById('toggleHot');

let doc = null;
let showHot = false;
let pan = { x: 0, y: 0, k: 1 };

function el(name, attrs, text) {
  const n = document.createElementNS(SVG_NS, name);
  for (const k in attrs) n.setAttribute(k, attrs[k]);
  if (text != null) n.textContent = text;
  return n;
}

function escapeHtml(s) {
  return String(s).replace(/[&<>"]/g, c =>
    ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' }[c]));
}

function colourFor(g) {
  if (g.terminal) return 'var(--accent)';
  if (g.type === 'SR_LATCH' || g.type === 'FEEDBACK') return '#b07fd0';
  if (g.type === 'GENERIC') return '#e0b050';
  return '#5a8ac0';
}

function render() {
  if (!doc) return;
  view.textContent = '';
  view.setAttribute('width', stage.clientWidth);
  view.setAttribute('height', stage.clientHeight);

  const root = el('g', { transform: `translate(${pan.x},${pan.y}) scale(${pan.k})` });
  view.appendChild(root);

  // Count consumers per output pin so branch points can be dotted. Without a
  // dot there is no way to tell a junction from a crossing.
  const fanout = new Map();
  for (const w of doc.wires) {
    const key = w.fromGate + ':' + w.fromPin;
    fanout.set(key, (fanout.get(key) || 0) + 1);
  }

  for (const w of doc.wires) {
    const pts = [];
    for (let i = 0; i < w.points.length; i += 2) {
      pts.push(w.points[i] + ',' + w.points[i + 1]);
    }
    const base = w.feedback ? 'var(--muted)' : 'var(--wire)';
    const width = w.feedback ? 1.2 : 1.4;
    const line = el('polyline', {
      points: pts.join(' '),
      fill: 'none',
      stroke: base,
      'stroke-width': width,
      'stroke-dasharray': w.feedback ? '4 3' : 'none'
    });
    line.addEventListener('pointerenter', () => {
      line.setAttribute('stroke', 'var(--accent)');
      line.setAttribute('stroke-width', 2.6);
    });
    line.addEventListener('pointerleave', () => {
      line.setAttribute('stroke', base);
      line.setAttribute('stroke-width', width);
    });
    root.appendChild(line);
  }

  for (const [key, count] of fanout) {
    if (count < 2) continue;
    const w = doc.wires.find(x => x.fromGate + ':' + x.fromPin === key);
    root.appendChild(el('circle', {
      cx: w.points[0], cy: w.points[1], r: 2.6, fill: 'var(--wire)'
    }));
  }

  const hot = new Set(doc.criticalGates || []);
  for (const g of doc.gates) {
    const [x, y, w, h] = g.box;
    const group = el('g', {});
    const lit = showHot && hot.has(g.id);

    if (g.terminal) {
      group.appendChild(el('rect', {
        x, y: y + h / 2 - 15, width: w, height: 30, rx: 10,
        fill: 'none',
        stroke: lit ? 'var(--hot)' : colourFor(g),
        'stroke-width': lit ? 2.6 : 1.6
      }));
    } else {
      group.appendChild(el('rect', {
        x, y, width: w, height: h, rx: 4,
        fill: 'var(--panel)',
        stroke: lit ? 'var(--hot)' : colourFor(g),
        'stroke-width': lit ? 2.6 : 1.6
      }));
    }

    if (g.negated) {
      group.appendChild(el('circle', {
        cx: x + w + 5, cy: y + h / 2, r: 5,
        fill: 'var(--bg)', stroke: 'var(--ink)', 'stroke-width': 1.6
      }));
    }

    group.appendChild(el('text', {
      x: x + w / 2, y: y + h / 2 + 4, 'text-anchor': 'middle',
      fill: 'var(--ink)', 'font-size': 12, 'font-weight': 600
    }, g.terminal ? g.label : g.symbol));

    if (!g.terminal) {
      group.appendChild(el('text', {
        x: x + w / 2, y: y + h + 12, 'text-anchor': 'middle',
        fill: 'var(--muted)', 'font-size': 9
      }, g.label));
    }
    if (g.delayTicks) {
      group.appendChild(el('text', {
        x: x + w / 2, y: y - 6, 'text-anchor': 'middle',
        fill: 'var(--muted)', 'font-size': 9
      }, 'Δ ' + g.delayTicks + 't'));
    }

    const title = el('title', {});
    title.textContent = `${g.symbol} ${g.label} at ${g.pos.join(',')}`
      + (g.delayTicks ? ` (${g.delayTicks} tick${g.delayTicks === 1 ? '' : 's'})` : '')
      + (g.note ? '\n' + g.note : '');
    group.appendChild(title);
    root.appendChild(group);
  }
}

function sidebar() {
  const list = items => items && items.length
    ? '<ul>' + items.map(s => `<li>${escapeHtml(s)}</li>`).join('') + '</ul>'
    : '<p style="color:var(--muted)">none</p>';

  const warnings = (doc.timing.warnings || []).concat(doc.notes || []);
  side.innerHTML =
    `<h2>Timing</h2>
     <p>Critical path <strong>${doc.timing.criticalPathTicks}</strong> redstone ticks<br>
     <span style="color:var(--muted)">minimum clock
     ${doc.timing.minClockGameTicks} game ticks</span></p>
     <h2>Gates</h2>
     ${list(Object.entries(doc.tally || {}).map(([k, v]) => `${k}: ${v}`))}
     <h2>Recognised</h2>
     ${list(doc.patterns)}
     <h2>Warnings</h2>
     <div class="warn">${list(warnings)}</div>`;
}

function fit() {
  if (!doc) return;
  const k = Math.min(stage.clientWidth / (doc.width || 1),
                     stage.clientHeight / (doc.height || 1)) * 0.92;
  pan = {
    k,
    x: (stage.clientWidth - doc.width * k) / 2,
    y: (stage.clientHeight - doc.height * k) / 2
  };
  render();
}

function load(text, name) {
  // An exported SVG is already self-contained, with its own dark mode and
  // hover styling, so it just gets shown as-is.
  if (name.endsWith('.svg') || text.trimStart().startsWith('<?xml')) {
    drop.classList.add('hide');
    view.outerHTML = text;
    summary.textContent = name;
    side.innerHTML = '<p style="color:var(--muted)">SVG loaded. Open a '
      + '<code>.graph.json</code> for timing, warnings and the critical path.</p>';
    doc = null;
    return;
  }

  let parsed;
  try {
    parsed = JSON.parse(text);
  } catch (e) {
    alert('That file is not valid JSON.');
    return;
  }
  if (!parsed.format || !String(parsed.format).startsWith('redstonelogics.graph/')) {
    alert('Not a RedstoneLogics graph document. Export "graph JSON", not the '
        + 'block snapshot.');
    return;
  }
  doc = parsed;
  drop.classList.add('hide');
  summary.textContent = doc.summary || '';
  sidebar();
  fit();
}

function openFile(f) {
  if (f) f.text().then(t => load(t, f.name.toLowerCase()));
}

document.getElementById('file').addEventListener('change', e => openFile(e.target.files[0]));
['dragenter', 'dragover'].forEach(t =>
  stage.addEventListener(t, e => e.preventDefault()));
stage.addEventListener('drop', e => {
  e.preventDefault();
  openFile(e.dataTransfer.files[0]);
});
document.getElementById('fit').addEventListener('click', fit);
hotButton.addEventListener('click', () => {
  showHot = !showHot;
  hotButton.setAttribute('aria-pressed', String(showHot));
  render();
});

let dragging = null;
stage.addEventListener('pointerdown', e => {
  dragging = { x: e.clientX - pan.x, y: e.clientY - pan.y };
  stage.classList.add('drag');
});
stage.addEventListener('pointermove', e => {
  if (!dragging) return;
  pan.x = e.clientX - dragging.x;
  pan.y = e.clientY - dragging.y;
  render();
});
['pointerup', 'pointercancel', 'pointerleave'].forEach(t =>
  stage.addEventListener(t, () => {
    dragging = null;
    stage.classList.remove('drag');
  }));
stage.addEventListener('wheel', e => {
  if (!doc) return;
  e.preventDefault();
  const f = e.deltaY > 0 ? 1 / 1.1 : 1.1;
  const r = stage.getBoundingClientRect();
  const mx = e.clientX - r.left;
  const my = e.clientY - r.top;
  pan.x = mx - (mx - pan.x) * f;
  pan.y = my - (my - pan.y) * f;
  pan.k *= f;
  render();
}, { passive: false });
window.addEventListener('resize', render);
