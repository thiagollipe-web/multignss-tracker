// Animação em blocos (voxel), 30 s, 1080x1920. Tudo desenhado a partir de t (segundos):
// o mesmo draw(t) serve para tocar no navegador e para gravar quadro a quadro.
"use strict";

const W = 1080, H = 1920, DURATION = 30, FPS = 30;
const GROUND_Y = 1010, ROAD_BOTTOM = 1160, CAR_X = 380, BLOCK = 60;
const YELLOW = "#FFC400", INK = "#1A1A1A";
const SAT = [
  { name: "GLONASS", color: "#FF5A5A", x: 200 },
  { name: "Galileo", color: "#B388FF", x: 540 },
  { name: "BeiDou", color: "#39FF88", x: 880 },
];
const CAPTIONS = [
  { a: 0.3, b: 4.8, text: "Partiu viagem!" },
  { a: 5.2, b: 9.8, text: "Opa… onde estou?" },
  { a: 10.2, b: 14.8, text: "Sem sinal. Totalmente perdido." },
  { a: 15.2, b: 18.9, text: "Lembrei do MultiGNSS Tracker!" },
  { a: 19.2, b: 24.8, text: "Satélites guiando, mesmo offline." },
  { a: 25.2, b: 27.9, text: "Rota certa, sem estresse." },
  { a: 28.2, b: 30, text: "Baixe grátis no GitHub." },
];

const cv = document.getElementById("c");
const g = cv.getContext("2d");
const orbi = new Image();
const ready = new Promise((ok) => { orbi.onload = ok; orbi.onerror = ok; });
orbi.src = ORBI_SRC;

// ---------- utilidades ----------
const clamp = (v, a = 0, b = 1) => Math.min(b, Math.max(a, v));
const seg = (t, a, b) => clamp((t - a) / (b - a));
const ease = (x) => x * x * (3 - 2 * x);
const lerp = (a, b, x) => a + (b - a) * x;
const hash = (n) => { const x = Math.sin(n * 127.1 + 311.7) * 43758.5453; return x - Math.floor(x); };
function mixHex(c1, c2, x) {
  const p = (c) => [1, 3, 5].map((i) => parseInt(c.slice(i, i + 2), 16));
  const [a, b] = [p(c1), p(c2)];
  return "#" + a.map((v, i) => Math.round(lerp(v, b[i], x)).toString(16).padStart(2, "0")).join("");
}

// bloco com luz em cima e sombra embaixo (cara de voxel)
function block(x, y, w, h, color, edge = 6) {
  g.fillStyle = color; g.fillRect(x, y, w, h);
  g.fillStyle = "rgba(255,255,255,.18)"; g.fillRect(x, y, w, edge);
  g.fillStyle = "rgba(0,0,0,.22)"; g.fillRect(x, y + h - edge, w, edge);
}

function text(str, x, y, size, color = "#fff", align = "center") {
  g.font = `900 ${size}px "Courier New", monospace`;
  g.textAlign = align; g.textBaseline = "middle";
  g.lineWidth = size / 6; g.strokeStyle = INK; g.lineJoin = "round";
  g.strokeText(str, x, y); g.fillStyle = color; g.fillText(str, x, y);
}

// ---------- câmera: velocidade por cena, integrada uma vez ----------
function speed(t) {
  if (t < 5) return 420;
  if (t < 10) return lerp(420, 0, ease(seg(t, 5, 9.5)));
  if (t < 15) return 300 * Math.sin(((t - 10) * Math.PI) / 1.25); // anda em círculos
  if (t < 22) return 0;
  return lerp(0, 380, ease(seg(t, 22, 23.5)));
}
const camTable = [0], dirTable = [1];
for (let f = 1; f <= DURATION * FPS; f++) {
  const v = speed(f / FPS);
  camTable.push(camTable[f - 1] + v / FPS);
  dirTable.push(v > 1 ? 1 : v < -1 ? -1 : dirTable[f - 1]);
}
const frameOf = (t) => clamp(Math.round(t * FPS), 0, DURATION * FPS);
const camX = (t) => camTable[frameOf(t)];
const SIGN_X = camTable[frameOf(9.5)] + CAR_X + 330;

// noite: escurece ao se perder, clareia quando o app guia
const night = (t) => ease(seg(t, 9, 13)) * (1 - ease(seg(t, 20, 25)));

// ---------- cenário ----------
function sky(t) {
  const n = night(t);
  const top = mixHex("#6EC1FF", "#0B1030", n), bot = mixHex("#CDEBFF", "#2A2456", n);
  const grd = g.createLinearGradient(0, 0, 0, GROUND_Y);
  grd.addColorStop(0, top); grd.addColorStop(1, bot);
  g.fillStyle = grd; g.fillRect(0, 0, W, GROUND_Y);
  // estrelas
  if (n > 0) for (let i = 0; i < 60; i++) {
    g.fillStyle = `rgba(255,255,255,${n * (0.4 + 0.6 * hash(i + 9))})`;
    const s = 6 + Math.round(hash(i) * 6);
    g.fillRect(hash(i * 3) * W, hash(i * 7) * 700, s, s);
  }
  // sol/lua em blocos
  const sx = 820, sy = lerp(260, 330, n);
  block(sx, sy, 120, 120, mixHex("#FFE36E", "#E8E6D8", n), 10);
  // nuvens
  for (let i = 0; i < 6; i++) {
    const x = ((i * 420 - camX(t) * 0.15) % 2520 + 2520) % 2520 - 300;
    const y = 160 + hash(i) * 320;
    g.fillStyle = `rgba(255,255,255,${0.9 - n * 0.6})`;
    g.fillRect(x, y, 240, 60); g.fillRect(x + 60, y - 40, 120, 40);
  }
}

function hills(t) {
  const n = night(t), off = camX(t) * 0.35;
  const first = Math.floor(off / BLOCK) - 1;
  for (let i = first; i < first + W / BLOCK + 3; i++) {
    const hgt = (3 + Math.floor(hash(i * 0.37) * 4 + Math.sin(i * 0.25) * 2)) * BLOCK;
    const x = i * BLOCK - off;
    block(x, GROUND_Y - hgt, BLOCK, hgt, mixHex("#4E9A3A", "#1D3A2A", n), 0);
    g.fillStyle = "rgba(0,0,0,.12)"; g.fillRect(x, GROUND_Y - hgt, 2, hgt);
  }
}

function trees(t) {
  const n = night(t), off = camX(t);
  const first = Math.floor((off - 300) / 480);
  for (let i = first; i < first + 5; i++) {
    if (hash(i * 5.3) < 0.35) continue;
    const x = i * 480 + 120 - off;
    block(x + 60, GROUND_Y - 150, 40, 150, mixHex("#7A5230", "#3B2A1A", n), 4);
    const leaf = mixHex("#3FA34D", "#1E4A2C", n);
    for (let r = 0; r < 3; r++) for (let c = 0; c < 3; c++) block(x + c * 54, GROUND_Y - 300 + r * 54, 54, 54, leaf, 5);
  }
}

function ground(t) {
  const n = night(t), off = camX(t);
  // estrada
  g.fillStyle = mixHex("#6B6B6B", "#2E2E36", n); g.fillRect(0, GROUND_Y, W, ROAD_BOTTOM - GROUND_Y);
  g.fillStyle = mixHex("#EDEDED", "#8A8A90", n);
  for (let x = -((off % 160) + 160); x < W; x += 160) g.fillRect(x, GROUND_Y + 66, 80, 14);
  // grama e terra em blocos
  const first = Math.floor(off / BLOCK) - 1;
  for (let i = first; i < first + W / BLOCK + 3; i++) {
    const x = i * BLOCK - off;
    block(x, ROAD_BOTTOM, BLOCK, BLOCK, mixHex("#5DBB45", "#24502A", n), 8);
    for (let r = 1; r * BLOCK + ROAD_BOTTOM < H; r++) {
      const y = ROAD_BOTTOM + r * BLOCK, k = hash(i * 13 + r);
      if (r < 5) { block(x, y, BLOCK, BLOCK, mixHex(mixHex("#8B5A2B", "#6E4520", k * 0.6), "#2B1C12", n), 5); continue; }
      block(x, y, BLOCK, BLOCK, mixHex(mixHex("#8A8A8A", "#6F6F6F", k), "#26262C", n * 0.7), 5); // pedra
      if (k > 0.86) {                                // minério nas cores das constelações
        g.fillStyle = SAT[Math.floor(hash(i * 7 + r * 3) * 3)].color;
        g.fillRect(x + 12, y + 14, 12, 12); g.fillRect(x + 34, y + 30, 12, 12); g.fillRect(x + 18, y + 38, 8, 8);
      }
    }
  }
}

function signs(t) {
  const x = SIGN_X - camX(t);
  if (x < -300 || x > W + 300) return;
  block(x, GROUND_Y - 260, 24, 260, "#7A5230", 4);
  [[-1, GROUND_Y - 250, "#C9A06A"], [1, GROUND_Y - 170, "#B88F5A"]].forEach(([d, y, c]) => {
    const bx = d > 0 ? x + 24 : x - 200;
    block(bx, y, 200, 64, c, 6);
    g.fillStyle = c; g.beginPath();
    const tip = d > 0 ? bx + 240 : bx - 40, base = d > 0 ? bx + 200 : bx;
    g.moveTo(base, y - 10); g.lineTo(tip, y + 32); g.lineTo(base, y + 74); g.fill();
    text("???", bx + 100, y + 33, 38, "#fff");
  });
}

// ---------- personagem e carro ----------
function face(x, y, mood, t) {
  block(x, y, 80, 80, "#E8B07A", 0);              // cabeça
  block(x, y, 80, 22, "#5A3A1E", 0);              // cabelo
  g.fillStyle = INK;
  if (mood === "cool") {                          // óculos escuros
    g.fillRect(x + 8, y + 32, 64, 16);
  } else {
    const blink = Math.sin(t * 2.3) > 0.97 ? 3 : 12;
    g.fillRect(x + 16, y + 34, 12, blink); g.fillRect(x + 52, y + 34, 12, blink);
  }
  if (mood === "happy" || mood === "cool") { g.fillRect(x + 22, y + 60, 36, 6); g.fillRect(x + 18, y + 54, 6, 8); g.fillRect(x + 56, y + 54, 6, 8); }
  else if (mood === "lost") { g.fillRect(x + 24, y + 62, 32, 6); g.fillRect(x + 12, y + 26, 18, 5); }
  else if (mood === "panic") { g.fillRect(x + 30, y + 56, 20, 16); g.fillStyle = "#7FD0FF"; g.fillRect(x + 72, y + 18 + ((t * 60) % 30), 10, 14); }
  else { g.fillRect(x + 30, y + 58, 20, 10); } // surpresa
}

function car(t, mood) {
  const f = frameOf(t), dir = dirTable[f], moving = Math.abs(speed(t)) > 1;
  const bob = moving ? Math.round(Math.sin(t * 22) * 3) : 0;
  g.save();
  g.translate(CAR_X, bob);
  g.scale(dir, 1);
  // farol aceso à noite
  const n = night(t);
  if (n > 0.2) {
    g.fillStyle = `rgba(255,236,150,${0.35 * n})`;
    g.beginPath(); g.moveTo(170, 1050); g.lineTo(560, 990); g.lineTo(560, 1150); g.fill();
  }
  block(-180, 1010, 360, 100, "#D63A2F", 10);      // carroceria
  block(-90, 920, 190, 92, "#B83026", 8);          // cabine
  block(-74, 934, 72, 66, "#9FD8FF", 0);           // janela traseira
  block(16, 934, 72, 66, "#9FD8FF", 0);            // janela da frente
  block(160, 1030, 22, 26, "#FFE36E", 0);          // farol
  block(-182, 1036, 14, 22, "#FF8A80", 0);         // lanterna
  g.restore();
  // cabeça na janela (sem espelhar o rosto)
  const hx = CAR_X + (dir > 0 ? 6 : -86);
  face(hx, 904 + bob, mood, t);
  // rodas quadradas girando
  [-110, 110].forEach((wx) => {
    g.save();
    g.translate(CAR_X + wx * dir, 1124 + bob);
    g.rotate((camX(t) / 36) % (Math.PI * 2));
    block(-36, -36, 72, 72, "#222", 6);
    g.fillStyle = "#9A9A9A"; g.fillRect(-12, -12, 24, 24);
    g.restore();
  });
}

function bubble(x, y, w, h, draw) {
  block(x, y, w, h, "#FFFFFF", 6);
  g.fillStyle = "#fff"; g.fillRect(x + 30, y + h, 24, 24); g.fillRect(x + 18, y + h + 24, 12, 12);
  draw(x, y);
}

function lightbulb(t) {
  const p = ease(seg(t, 15.2, 15.6)) * (1 - ease(seg(t, 18.6, 19.2))), x = CAR_X + 40, y = 760 - p * 30;
  if (p <= 0) return;
  g.globalAlpha = p;
  const glow = 0.5 + 0.5 * Math.sin(t * 12);
  g.fillStyle = `rgba(255,228,110,${0.35 + 0.25 * glow})`;
  for (let i = 0; i < 8; i++) {
    const a = (i / 8) * Math.PI * 2;
    g.fillRect(x + Math.cos(a) * 110 - 10, y + Math.sin(a) * 110 - 10, 20, 20);
  }
  block(x - 50, y - 60, 100, 90, "#FFE36E", 8);
  block(x - 30, y + 30, 60, 34, "#9A9A9A", 5);
  g.globalAlpha = 1;
}

function satellites(t) {
  const p = ease(seg(t, 19.2, 20.2));
  if (p <= 0 || t > 26) return;
  const out = ease(seg(t, 25, 26));
  SAT.forEach((s, i) => {
    const y = lerp(-200, 230 + i % 2 * 70, p) - out * 400 + Math.sin(t * 2 + i) * 10;
    block(s.x - 110, y + 10, 60, 40, s.color, 5);
    block(s.x + 50, y + 10, 60, 40, s.color, 5);
    g.fillStyle = INK; g.fillRect(s.x - 50, y + 26, 100, 8);
    block(s.x - 40, y - 10, 80, 80, YELLOW, 8);
    text(s.name, s.x, y + 110, 30, s.color);
    // feixes até o carro
    if (t > 22 && t < 25.5) {
      g.save();
      g.setLineDash([22, 18]); g.lineDashOffset = -t * 120;
      g.strokeStyle = s.color; g.lineWidth = 8;
      g.beginPath(); g.moveTo(s.x, y + 70); g.lineTo(CAR_X, 900); g.stroke();
      g.restore();
    }
  });
}

function routeOnRoad(t) {
  const p = ease(seg(t, 22, 23));
  if (p <= 0) return;
  const end = lerp(CAR_X + 180, W, p);
  g.fillStyle = INK; g.fillRect(CAR_X + 170, ROAD_BOTTOM - 34, end - CAR_X - 170, 28);
  g.fillStyle = YELLOW; g.fillRect(CAR_X + 170, ROAD_BOTTOM - 30, end - CAR_X - 170, 20);
  g.fillStyle = INK;
  for (let x = CAR_X + 200 - ((camX(t) * 1) % 80); x < end - 20; x += 80) {
    if (x < CAR_X + 180) continue;
    g.fillRect(x, ROAD_BOTTOM - 26, 6, 12); g.fillRect(x + 6, ROAD_BOTTOM - 22, 6, 4);
  }
}

// ---------- celular com o app ----------
function phone(t) {
  const inP = ease(seg(t, 15.8, 16.6)), outP = ease(seg(t, 21.6, 22.2));
  if (inP <= 0 || outP >= 1) return;
  const px = 300, pw = 480, ph = 820, py = lerp(H + 40, 300, inP) + outP * (H - 300);
  block(px, py, pw, ph, INK, 10);
  const sx = px + 24, sy = py + 60, sw = pw - 48, sh = ph - 120;
  if (t < 18.6) {                                  // abrindo o app
    g.fillStyle = "#FFF4CC"; g.fillRect(sx, sy, sw, sh);
    const s = 220 + Math.sin(t * 6) * 8;
    g.drawImage(orbi, px + pw / 2 - s / 2, sy + 120, s, s);
    text("MultiGNSS", px + pw / 2, sy + 420, 46, YELLOW);
    text("Tracker", px + pw / 2, sy + 476, 46, YELLOW);
    text("abrindo" + ".".repeat(1 + Math.floor(t * 3) % 3), px + pw / 2, sy + 560, 30, "#fff");
  } else {                                         // navegação
    g.fillStyle = "#F2EEDF"; g.fillRect(sx, sy, sw, sh);
    g.fillStyle = "#FFFFFF";
    for (let i = 0; i < 5; i++) { g.fillRect(sx, sy + 120 + i * 120, sw, 26); g.fillRect(sx + 40 + i * 100, sy, 26, sh); }
    const d = ease(seg(t, 18.8, 20.4));            // rota se desenhando
    const pts = [[sx + 66, sy + sh - 40], [sx + 66, sy + 373], [sx + 253, sy + 373], [sx + 253, sy + 133], [sx + 380, sy + 133]];
    g.lineCap = "round"; g.lineJoin = "round";
    [[INK, 30], [YELLOW, 18]].forEach(([c, w]) => {
      g.strokeStyle = c; g.lineWidth = w; g.beginPath(); g.moveTo(...pts[0]);
      const total = pts.length - 1, upto = d * total;
      for (let i = 1; i <= total; i++) {
        const k = clamp(upto - (i - 1));
        if (k <= 0) break;
        g.lineTo(lerp(pts[i - 1][0], pts[i][0], k), lerp(pts[i - 1][1], pts[i][1], k));
      }
      g.stroke();
    });
    // seta de manobra
    block(sx + 12, sy + 12, sw - 24, 96, YELLOW, 8);
    g.fillStyle = INK;
    g.fillRect(sx + 40, sy + 40, 14, 50); g.fillRect(sx + 40, sy + 40, 46, 14);
    g.beginPath(); g.moveTo(sx + 100, sy + 47); g.lineTo(sx + 80, sy + 28); g.lineTo(sx + 80, sy + 66); g.fill();
    text("200 m", sx + 230, sy + 60, 44, INK);
    g.drawImage(orbi, sx + sw - 120, sy + sh - 130, 110, 110);
  }
  block(px + pw / 2 - 50, py + 20, 100, 16, "#333", 0);
}

function noSignal(t) {
  if (t < 11 || t > 15) return;
  bubble(CAR_X + 120, 690, 300, 150, (x, y) => {
    block(x + 30, y + 24, 60, 100, INK, 5);
    g.fillStyle = "#FF3B30"; g.fillRect(x + 42, y + 50, 36, 8); g.fillRect(x + 56, y + 36, 8, 36);
    text("sem", x + 190, y + 52, 40, INK); text("sinal", x + 190, y + 100, 40, "#FF3B30");
  });
}

function questionMarks(t) {
  if (t < 6 || t > 11) return;
  [0, 1, 2].forEach((i) => {
    const show = seg(t, 6 + i * 0.5, 6.3 + i * 0.5);
    if (show <= 0) return;
    text("?", CAR_X - 60 + i * 90, 820 - i * 40 + Math.sin(t * 5 + i) * 10, 90 * show, YELLOW);
  });
}

function orbiBuddy(t) {
  const p = ease(seg(t, 25, 25.6));
  if (p <= 0) return;
  const x = lerp(W + 200, CAR_X + 330, p), y = 700 + Math.sin(t * 4) * 18;
  g.save(); g.translate(x, y); g.rotate(Math.sin(t * 6) * 0.15);
  g.drawImage(orbi, -110, -110, 220, 220); g.restore();
  text("Bora!", x, y - 150, 44, YELLOW);
}

function caption(t) {
  const c = CAPTIONS.find((k) => t >= k.a && t < k.b);
  if (!c) return;
  const words = c.text.split(" "), active = Math.min(words.length - 1, Math.floor(seg(t, c.a, c.b - 0.4) * words.length));
  g.font = '900 60px "Courier New", monospace';
  const lines = [], maxW = 920;
  let line = [];
  words.forEach((w, i) => {
    const test = [...line, w].join(" ");
    if (g.measureText(test).width > maxW && line.length) { lines.push(line); line = []; }
    line.push({ w, i });
  });
  lines.push(line);
  const lh = 78, top = 1330 - (lines.length - 1) * lh / 2;
  g.fillStyle = "rgba(10,9,6,.72)";
  g.fillRect(60, top - 60, W - 120, lines.length * lh + 42);
  lines.forEach((ln, li) => {
    const full = ln.map((o) => o.w).join(" ");
    let x = W / 2 - g.measureText(full).width / 2;
    ln.forEach((o) => {
      const s = o.w + " ";
      text(o.w, x, top + li * lh, 60, o.i === active ? YELLOW : "#fff", "left");
      x += g.measureText(s).width;
    });
  });
}

function endCard(t) {
  const p = ease(seg(t, 27.9, 28.5));
  if (p <= 0) return;
  g.fillStyle = `rgba(10,9,6,${0.8 * p})`; g.fillRect(0, 0, W, H);
  const y = lerp(H, 380, p);
  block(90, y, 900, 760, "#14120C", 12);
  g.strokeStyle = YELLOW; g.lineWidth = 8; g.strokeRect(94, y + 4, 892, 752);
  const s = 280 + Math.sin(t * 5) * 10;
  g.drawImage(orbi, W / 2 - s / 2, y + 40, s, s);
  text("MultiGNSS Tracker", W / 2, y + 380, 66, YELLOW);
  text("Grátis · Android · Offline", W / 2, y + 460, 40, "#fff");
  text("github.com/", W / 2, y + 570, 38, "#B388FF");
  text("thiagollipe-web/multignss-tracker", W / 2, y + 625, 36, "#B388FF");
}

// ---------- cena completa ----------
function moodAt(t) {
  if (t < 5.5) return "happy";
  if (t < 10) return "lost";
  if (t < 15.2) return "panic";
  if (t < 19) return "wow";
  if (t < 24.5) return "happy";
  return "cool";
}

function draw(t) {
  g.clearRect(0, 0, W, H);
  sky(t); satellites(t); hills(t); trees(t); ground(t); signs(t);
  routeOnRoad(t); car(t, moodAt(t)); questionMarks(t); noSignal(t); lightbulb(t); orbiBuddy(t);
  phone(t); endCard(t); caption(t);
}

window.renderAt = (t) => draw(clamp(t, 0, DURATION - 1e-3));
window.animReady = ready;

// tocar no navegador (fora do modo de gravação)
if (location.hash === "#capture") {
  document.body.classList.add("capture");
} else {
  let start = null, running = false;
  const loop = (now) => {
    running = true;
    if (start === null) start = now;
    const t = (now - start) / 1000;
    draw(Math.min(t, DURATION - 1e-3));
    if (t < DURATION) requestAnimationFrame(loop); else running = false;
  };
  ready.then(() => requestAnimationFrame(loop));
  document.getElementById("replay").addEventListener("click", () => { start = null; if (!running) requestAnimationFrame(loop); });
}
