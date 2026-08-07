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

let canGuess = true;
let mapExpanded = false;
const floatingMap = () => document.getElementById('floating-map');
const mapModal = () => document.getElementById('map-modal');
const modalContent = () => document.querySelector('.map-modal-content');

// ── INIT ──────────────────────────────────────────────────
window.onload = () => {
  if (token && playerId) {
    showGame();
    startGame();
  } else {
    showLogin();
  }

  document
    .getElementById('expand-map-btn')
    .addEventListener('click', expandMap);

  document
    .getElementById('close-map-btn')
    .addEventListener('click', collapseMap);

  document.addEventListener('keydown', (e) => {
    if (e.key === 'Escape') {
      collapseMap();
    }

    if (e.key.toLowerCase() === 'm') {
      if (mapExpanded) {
        collapseMap();
      } else {
        expandMap();
      }
    }
  });
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
function mapClickHandler(e) {
  if (!canGuess) return;

  if (
    document.getElementById('guess-btn').disabled &&
    document.getElementById('next-btn').style.display !== 'none'
  ) return;

  placeGuessPin(e.latlng.lat, e.latlng.lng);
}

function initMap() {
  if (map) return;

  map = L.map('world-map', {
    center: [20, 0],
    zoom: 2,
    minZoom: 2,
    maxZoom: 8,
    zoomControl: false,
    worldCopyJump: true,
    scrollWheelZoom: false,
    doubleClickZoom: false,
    attributionControl: false
  });

  L.control.zoom({
    position: 'bottomleft'
  }).addTo(map);

  L.tileLayer('https://{s}.basemaps.cartocdn.com/dark_all/{z}/{x}/{y}{r}.png', {
    attribution: '© OpenStreetMap © CARTO'
  }).addTo(map);

  map.getContainer().addEventListener('mouseenter', () => {
    map.scrollWheelZoom.disable();
  });

  document.addEventListener('keydown', (e) => {
    if (e.ctrlKey) {
      map.scrollWheelZoom.enable();
    }
  });

  document.addEventListener('keyup', () => {
    map.scrollWheelZoom.disable();
  });

  map.on('click', mapClickHandler);
}

function expandMap() {
  if (mapExpanded) return;

  mapExpanded = true;
  mapModal().classList.add('active');

  modalContent().appendChild(
    document.getElementById('world-map')
  );

  setTimeout(() => {
    map.invalidateSize();
  }, 250);
}

function collapseMap() {
  if (!mapExpanded) return;

  mapExpanded = false;

  floatingMap().appendChild(
    document.getElementById('world-map')
  );

  mapModal().classList.remove('active');

  setTimeout(() => {
    map.invalidateSize();
  }, 250);
}

function placeGuessPin(lat, lng) {
  const icon = L.divIcon({
    className: '',
    html: `<div class="guess-marker"></div>`,
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
  canGuess = true;

  document.getElementById('result-ribbon').style.display = 'none';
  document.getElementById('guess-btn').disabled = true;
  document.getElementById('guess-btn').style.display = 'block';
  document.getElementById('next-btn').style.display = 'none';

  document.getElementById('round-num').textContent = roundNum;
  document.getElementById('pin-hint').textContent = 'Click on map';
  document.getElementById('img-loader').style.display = 'flex';
  document.getElementById('location-img').style.display = 'none';

  if (guessMarker) {
    map.removeLayer(guessMarker);
    guessMarker = null;
  }

  if (actualMarker) {
    map.removeLayer(actualMarker);
    actualMarker = null;
  }

  if (distanceLine) {
    map.removeLayer(distanceLine);
    distanceLine = null;
  }

  if (map) {
    map.setView([20, 0], 2);
  }

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

function animateNumber(elementId, start, end, duration = 800) {
    const element = document.getElementById(elementId);
    const startTime = performance.now();

    function update(currentTime) {
        const progress = Math.min((currentTime - startTime) / duration, 1);
        const eased = 1 - Math.pow(1 - progress, 3);
        const value = Math.round(start + (end - start) * eased);
        element.textContent = value.toLocaleString();

        if (progress < 1) {
            requestAnimationFrame(update);
        }
    }

    requestAnimationFrame(update);
}

function animateScore(score){
    const element = document.getElementById('result-score');
    const start = performance.now();

    function frame(time){
        const progress = Math.min((time - start) / 800, 1);
        const value = Math.round(score * progress);
        element.textContent = value.toLocaleString() + " pts";

        if(progress < 1){
            requestAnimationFrame(frame);
        }
    }

    requestAnimationFrame(frame);
}

async function submitGuess() {
  if (!guessMarker) return;

  const { lat, lng } = guessMarker.getLatLng();

  stopTimer();
  canGuess = false;

  try {
    const data = await api(
      '/game/guess',
      'POST',
      {
        locationId: currentLocationId,
        guessLatitude: lat,
        guessLongitude: lng,
        timeTakenSeconds: elapsedSeconds
      },
      true
    );

    const previousScore = runningScore;
    runningScore += data.score;
    animateNumber(
        'running-score',
        previousScore,
        runningScore,
        900
    );

    const label = document.getElementById("result-label");
    label.className = "";
    const performance = data.performanceLabel.toUpperCase();
    label.textContent = performance;
    
    if(performance.includes("PERFECT")){
        label.classList.add("badge-perfect");
    } else if(performance.includes("EXCELLENT")){
        label.classList.add("badge-excellent");
    } else if(performance.includes("GOOD")){
        label.classList.add("badge-good");
    } else {
        label.classList.add("badge-average");
    }

    document.getElementById('result-place').textContent =
      `${data.city}, ${data.country}`;

    document.getElementById('result-dist').textContent =
      `${data.distanceKm.toFixed(1)} km`;

    document.getElementById('result-score').textContent = "0 pts";
    animateScore(data.score);

    setTimeout(() => {
        document.getElementById('result-ribbon').style.display = 'block';
    }, 350);

    const actualIcon = L.divIcon({
      className: '',
      html: `<div class="actual-marker"></div>`,
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

    const bounds = L.latLngBounds([
      [lat, lng],
      [data.actualLatitude, data.actualLongitude]
    ]);

    map.flyToBounds(bounds, {
      padding: [80, 80],
      duration: 1.5
    });

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