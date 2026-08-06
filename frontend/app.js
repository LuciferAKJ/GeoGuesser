// ── CONFIG ────────────────────────────────────────────────
const API = 'http://localhost:8080/api'; // change when deploying

// ── STATE ─────────────────────────────────────────────────
let token      = localStorage.getItem('token');
let playerId   = localStorage.getItem('playerId');
let username   = localStorage.getItem('username');

let map;
let guessMarker;
let actualMarker;
let distanceLine;

let currentLocationId = null;
let timerInterval = null;
let elapsedSeconds = 0;

let roundNum = 1;
let runningScore = 0;
const TOTAL_ROUNDS = 5;

// ── INIT ──────────────────────────────────────────────────
window.onload = () => {
  if (token && playerId) {
    showGame();
    startGame();
  } else {
    showLogin();
  }
};

// ── SCREENS ───────────────────────────────────────────────
function show(id) {
  document.getElementById('auth-screen').style.display = 'none';
  document.getElementById('game-screen').style.display = 'none';
  document.getElementById('lb-screen').style.display = 'none';

  const el = document.getElementById(id);
  el.style.display = 'flex';

  if (id === 'auth-screen' || id === 'lb-screen') {
    el.style.alignItems = 'center';
  }
}

function showLogin() {
  show('auth-screen');
  toggle('login-form', 'register-form');
}

function showRegister() {
  show('auth-screen');
  toggle('register-form', 'login-form');
}

function showGame() {
  show('game-screen');
  initMap();
}

function showLeaderboard() {
  show('lb-screen');
  loadLeaderboard();
}

function toggle(a, b) {
  document.getElementById(a).style.display = 'flex';
  document.getElementById(b).style.display = 'none';
}

// ── MAP ───────────────────────────────────────────────────
function initMap() {
  if (map) return;

  map = L.map('world-map', {
    center: [20, 0],
    zoom: 2,
    minZoom: 1
  });

  L.tileLayer('https://{s}.basemaps.cartocdn.com/dark_all/{z}/{x}/{y}{r}.png', {
    attribution: '© OpenStreetMap © CARTO'
  }).addTo(map);

  map.on('click', (e) => {
    if (document.getElementById('guess-btn').disabled &&
        document.getElementById('next-btn').style.display !== 'none') return;

    placeGuessPin(e.latlng.lat, e.latlng.lng);
  });
}

function placeGuessPin(lat, lng) {
  const icon = L.divIcon({
    className: '',
    html: `<div style="
      width:18px;height:18px;
      background:#e94560;
      border:3px solid #fff;
      border-radius:50%;
      box-shadow:0 2px 6px rgba(0,0,0,.5)">
    </div>`,
    iconAnchor: [9, 9]
  });

  if (guessMarker) map.removeLayer(guessMarker);

  guessMarker = L.marker([lat, lng], { icon }).addTo(map)
    .bindTooltip('Your guess');

  document.getElementById('guess-btn').disabled = false;
  document.getElementById('pin-hint').textContent =
    `📍 ${lat.toFixed(3)}, ${lng.toFixed(3)} selected`;
}

// ── AUTH ──────────────────────────────────────────────────
async function login() {
  const u = val('l-user');
  const p = val('l-pass');

  if (!u || !p) return msg('l-msg', 'Fill all fields', 'red');

  try {
    const data = await api('/auth/login', 'POST', {
      username: u,
      password: p
    });

    saveAuth(data);
    showGame();
    startGame();
  } catch (e) {
    msg('l-msg', e.message, 'red');
  }
}

async function register() {
  const u = val('r-user');
  const e = val('r-email');
  const p = val('r-pass');

  if (!u || !e || !p) return msg('r-msg', 'Fill all fields', 'red');

  try {
    await api('/auth/register', 'POST', {
      username: u,
      email: e,
      password: p
    });

    msg('r-msg', 'Account created!', 'green');
    setTimeout(showLogin, 1200);
  } catch (e) {
    msg('r-msg', e.message, 'red');
  }
}

function logout() {
  localStorage.clear();
  token = playerId = username = null;

  if (map) {
    map.remove();
    map = null;
  }

  stopTimer();
  showLogin();
}

function saveAuth(data) {
  token = data.token;
  playerId = data.playerId;
  username = data.username;

  localStorage.setItem('token', token);
  localStorage.setItem('playerId', playerId);
  localStorage.setItem('username', username);
}

// ── GAME ──────────────────────────────────────────────────
async function startGame() {
  roundNum = 1;
  runningScore = 0;

  document.getElementById('running-score').textContent = 0;
  document.getElementById('final-overlay').style.display = 'none';

  await loadRound();
}

async function loadRound() {
  document.getElementById('result-ribbon').style.display = 'none';
  document.getElementById('guess-btn').disabled = true;
  document.getElementById('guess-btn').style.display = 'block';
  document.getElementById('next-btn').style.display = 'none';

  document.getElementById('round-num').textContent = roundNum;
  document.getElementById('pin-hint').textContent = 'Click on map';
  document.getElementById('img-loader').style.display = 'flex';
  document.getElementById('location-img').style.display = 'none';

  if (guessMarker) map.removeLayer(guessMarker);

  stopTimer();

  try {
    const data = await api('/location/random');

    currentLocationId = data.id;

    const img = document.getElementById('location-img');

    img.onload = () => {
      document.getElementById('img-loader').style.display = 'none';
      img.style.display = 'block';
      startTimer();
    };

    img.src = data.imageUrl;
  } catch (e) {
    document.getElementById('img-loader').textContent = e.message;
  }
}

async function submitGuess() {
  if (!guessMarker) return;

  const { lat, lng } = guessMarker.getLatLng();

  stopTimer();

  try {
    const data = await api(
      `/game/guess?playerId=${playerId}`,
      'POST',
      {
        locationId: currentLocationId,
        guessLatitude: lat,
        guessLongitude: lng,
        timeTakenSeconds: elapsedSeconds
      },
      true
    );

    runningScore += data.score;
    document.getElementById('running-score').textContent =
      runningScore.toLocaleString();

    document.getElementById('result-label').textContent =
      data.performanceLabel;

    document.getElementById('result-place').textContent =
      `${data.city}, ${data.country}`;

    document.getElementById('result-dist').textContent =
      `${data.distanceKm.toFixed(1)} km`;

    document.getElementById('result-score').textContent =
      `${data.score} pts`;

    document.getElementById('result-ribbon').style.display = 'block';

    const actualIcon = L.divIcon({
      className: '',
      html: `<div style="width:18px;height:18px;background:#4caf50;border:3px solid #fff;border-radius:50%"></div>`,
      iconAnchor: [9, 9]
    });

    actualMarker = L.marker(
      [data.actualLatitude, data.actualLongitude],
      { icon: actualIcon }
    ).addTo(map);

    distanceLine = L.polyline(
      [[lat, lng], [data.actualLatitude, data.actualLongitude]],
      { color: '#f5c518', dashArray: '6,6' }
    ).addTo(map);

    map.fitBounds([
      [lat, lng],
      [data.actualLatitude, data.actualLongitude]
    ]);

    document.getElementById('guess-btn').style.display = 'none';

    if (roundNum >= TOTAL_ROUNDS) {
      setTimeout(showFinal, 1500);
    } else {
      document.getElementById('next-btn').style.display = 'block';
    }
  } catch (e) {
    alert(e.message);
  }
}

function nextRound() {
  roundNum++;
  loadRound();
}

function showFinal() {
  document.getElementById('final-score-val').textContent =
    runningScore.toLocaleString();

  document.getElementById('final-overlay').style.display = 'flex';
}

// ── TIMER ─────────────────────────────────────────────────
function startTimer() {
  elapsedSeconds = 0;

  timerInterval = setInterval(() => {
    elapsedSeconds++;
    document.getElementById('timer').textContent =
      elapsedSeconds + 's';
  }, 1000);
}

function stopTimer() {
  clearInterval(timerInterval);
}

// ── API ───────────────────────────────────────────────────
async function api(path, method = 'GET', body = null, auth = false) {
  const headers = {
    'Content-Type': 'application/json'
  };

  // Always send JWT if available
  if (token) {
    headers.Authorization = 'Bearer ' + token;
  }

  const res = await fetch(API + path, {
    method,
    headers,
    body: body ? JSON.stringify(body) : null
  });

  const text = await res.text();

  console.log(`${method} ${path}`);
  console.log("Status:", res.status);
  console.log("Body:", text);

  if (!text.trim()) {
    throw new Error(`Server returned empty response (HTTP ${res.status})`);
  }

  let data;
  try {
    data = JSON.parse(text);
  } catch {
    throw new Error(text);
  }

  if (!res.ok) {
    throw new Error(data.error || data.message || `HTTP ${res.status}`);
  }

  return data;
}
// ── UTILS ─────────────────────────────────────────────────
function val(id) {
  return document.getElementById(id).value.trim();
}

function msg(id, text, color) {
  const el = document.getElementById(id);

  el.textContent = text;
  el.style.color =
    color === 'red'
      ? '#e94560'
      : color === 'green'
      ? '#4caf50'
      : '#aaa';
}