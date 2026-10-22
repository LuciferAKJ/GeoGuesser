// ── LEADERBOARD ───────────────────────────────────────────
async function loadLeaderboard() {
    const tbody = document.getElementById('lb-body');

    tbody.innerHTML = `
        <tr>
            <td colspan="5" style="text-align:center;">
                Loading...
            </td>
        </tr>
    `;

    try {
        const players = await api('/player/leaderboard');

        if (!players.length) {
            tbody.innerHTML = `
                <tr>
                    <td colspan="5" style="text-align:center;">
                        No players yet.
                    </td>
                </tr>
            `;
            return;
        }

        tbody.innerHTML = players.map(player => {
            let rankDisplay = player.rank;
            if (player.rank === 1) rankDisplay = '🥇 1';
            else if (player.rank === 2) rankDisplay = '🥈 2';
            else if (player.rank === 3) rankDisplay = '🥉 3';

            return `
            <tr>
                <td class="lb-rank">${rankDisplay}</td>
                <td class="lb-user">${escapeHtml(player.username)}</td>
                <td class="lb-score">${Number(player.totalScore || 0).toLocaleString()}</td>
                <td class="lb-best">${Number(player.bestScore || 0).toLocaleString()}</td>
                <td>${player.gamesPlayed || 0}</td>
            </tr>
            `;
        }).join('');

    } catch (e) {
        tbody.innerHTML = `
            <tr>
                <td colspan="5" style="text-align:center; color:#e94560;">
                    Failed to load leaderboard
                </td>
            </tr>
        `;

        console.error('Leaderboard error:', e);
    }
}

// ── GAME HISTORY ───────────────────────────────────────────
async function loadHistory() {
  const tbody = document.getElementById('history-body');

  tbody.innerHTML = `
    <tr>
      <td colspan="6" style="text-align:center;">
        Loading...
      </td>
    </tr>
  `;

  try {
    const games = await api('/game/history');

    if (!games.length) {
      tbody.innerHTML = `
        <tr>
          <td colspan="6" style="text-align:center;">
            No games played yet.
          </td>
        </tr>
      `;
      return;
    }

    tbody.innerHTML = games.map((game, index) => {
      const playedAt = new Date(game.playedAt);

      const formattedDate = playedAt.toLocaleString([], {
        day: '2-digit',
        month: 'short',
        year: 'numeric',
        hour: '2-digit',
        minute: '2-digit'
      });

      return `
        <tr>
          <td>${index + 1}</td>

          <td>
            <strong>${escapeHtml(game.city)}</strong>
            <br>
            <span class="history-country">
              ${escapeHtml(game.country)}
            </span>
          </td>

          <td class="history-score">
            ${Number(game.score || 0).toLocaleString()}
          </td>

          <td>
            ${Number(game.distanceKm || 0).toFixed(1)} km
          </td>

          <td>
            ${game.timeTakenSeconds || 0}s
          </td>

          <td>
            ${formattedDate}
          </td>
        </tr>
      `;
    }).join('');

  } catch (e) {
    console.error('History error:', e);

    tbody.innerHTML = `
      <tr>
        <td colspan="6" style="text-align:center; color:#e94560;">
          Failed to load game history
        </td>
      </tr>
    `;
  }
}

function escapeHtml(value) {
    return String(value)
        .replace(/&/g, '&amp;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;')
        .replace(/"/g, '&quot;')
        .replace(/'/g, '&#039;');
}

// ── CONFIG ────────────────────────────────────────────────
const API = (typeof window !== 'undefined' && window.ENV && window.ENV.API_URL && window.ENV.API_URL.trim() !== '')
  ? window.ENV.API_URL.trim()
  : ((typeof localStorage !== 'undefined' && localStorage.getItem('GEOGUESSER_API_URL'))
     || 'http://localhost:8080/api');

// ── STATE ─────────────────────────────────────────────────
let token      = localStorage.getItem('token');
let playerId   = localStorage.getItem('playerId');
let username   = localStorage.getItem('username');

let map;
let guessMarker;
let actualMarker;
let distanceLine;

let currentLocationId = null;
let roundLocations = [];
let timerInterval = null;
let elapsedSeconds = 0;

let roundNum = 1;
let runningScore = 0;
const TOTAL_ROUNDS = 5;

let canGuess = true;
let isSubmittingGuess = false;

// ── INIT ──────────────────────────────────────────────────
window.onload = () => {
  const storedToken = localStorage.getItem('token');
  const storedPlayerId = localStorage.getItem('playerId');
  if (storedToken && storedToken !== 'null' && storedToken !== 'undefined' && storedPlayerId) {
    token = storedToken;
    playerId = storedPlayerId;
    username = localStorage.getItem('username');
    showGame();
    startGame();
  } else {
    logout();
  }
};

// ── SCREENS ───────────────────────────────────────────────
function show(id) {
  document.getElementById('auth-screen').style.display = 'none';
  document.getElementById('game-screen').style.display = 'none';
  document.getElementById('lb-screen').style.display = 'none';
  document.getElementById('history-screen').style.display = 'none';

  const el = document.getElementById(id);
  el.style.display = 'flex';

  if (
    id === 'auth-screen' ||
    id === 'lb-screen' ||
    id === 'history-screen'
  ) {
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

function showHistory() {
  show('history-screen');
  loadHistory();
}

function toggle(a, b) {
  document.getElementById(a).style.display = 'flex';
  document.getElementById(b).style.display = 'none';
}

// ── MAP ───────────────────────────────────────────────────
function mapClickHandler(e) {
  if (!canGuess) return;

  // Allow clicking as long as we're in an active round (before guess is submitted)
  if (document.getElementById('next-btn').style.display !== 'none') return;

  placeGuessPin(e.latlng.lat, e.latlng.lng);
}

function initMap() {
  if (map) return;

  map = L.map('world-map', {
    center: [20, 0],
    zoom: 2,
    minZoom: 2,
    maxZoom: 18,
    zoomControl: true,
    worldCopyJump: true,
    scrollWheelZoom: true,
    doubleClickZoom: true,
    dragging: true,
    touchZoom: true,
    boxZoom: false,
    attributionControl: false
  });

  L.tileLayer('https://server.arcgisonline.com/ArcGIS/rest/services/Canvas/World_Dark_Gray_Base/MapServer/tile/{z}/{y}/{x}', {
    attribution: 'Tiles &copy; Esri &mdash; Esri, DeLorme, NAVTEQ',
    maxZoom: 16
  }).addTo(map);

  L.control.attribution({
    position: 'bottomleft',
    prefix: false
  }).addAttribution('Tiles &copy; Esri').addTo(map);

  // Load country data and render zoom-tiered, collision-free labels
  let countryData = null;
  let countryLabelLayer = null;

  function updateCountryLabels() {
    if (!countryData || !map) return;

    const zoom = map.getZoom();

    if (countryLabelLayer) {
      map.removeLayer(countryLabelLayer);
      countryLabelLayer = null;
    }

    // Determine max priority by zoom level:
    // Low zoom (< 3): Only prominent Priority 1 anchor countries
    // Medium zoom (3 - 4.2): Priority 1 and 2 countries
    // High zoom (>= 4.2): All countries
    const maxPriority = zoom < 3 ? 1 : (zoom < 4.2 ? 2 : 3);

    let bounds;
    try {
      bounds = map.getBounds().pad(0.15);
    } catch {
      bounds = null;
    }

    // Filter features by priority and current view bounds
    const candidates = countryData.features
      .filter(f => {
        const priority = f.properties.priority || 3;
        if (priority > maxPriority) return false;
        if (bounds) {
          const coords = f.geometry.coordinates; // [lng, lat]
          return bounds.contains([coords[1], coords[0]]);
        }
        return true;
      })
      .sort((a, b) => (a.properties.priority || 3) - (b.properties.priority || 3));

    // Collision detection in screen space to prevent overlapping text
    const placedPoints = [];
    const minDx = zoom < 3 ? 65 : (zoom < 4.2 ? 48 : 36);
    const minDy = zoom < 3 ? 22 : (zoom < 4.2 ? 18 : 14);

    const markers = [];

    for (const feature of candidates) {
      const coords = feature.geometry.coordinates;
      const latlng = L.latLng(coords[1], coords[0]);
      const pt = map.latLngToLayerPoint(latlng);

      let collision = false;
      for (const p of placedPoints) {
        if (Math.abs(pt.x - p.x) < minDx && Math.abs(pt.y - p.y) < minDy) {
          collision = true;
          break;
        }
      }

      if (!collision) {
        placedPoints.push(pt);
        const priority = feature.properties.priority || 3;
        const labelMarker = L.marker(latlng, {
          icon: L.divIcon({
            className: 'country-label-wrapper',
            html: `<span class="country-label-text priority-${priority}">${escapeHtml(feature.properties.name)}</span>`,
            iconSize: [110, 18],
            iconAnchor: [55, 9]
          }),
          interactive: false
        });
        markers.push(labelMarker);
      }
    }

    countryLabelLayer = L.layerGroup(markers, { pane: 'tooltipPane' }).addTo(map);
  }

  fetch('countries.json')
    .then(response => response.json())
    .then(data => {
      countryData = data;
      updateCountryLabels();
    })
    .catch(err => console.warn('Country labels not loaded:', err));

  map.on('zoomend', updateCountryLabels);
  map.on('moveend', updateCountryLabels);

  // Floating map resize & touch / desktop expansion handling
  const mapContainer = document.getElementById('floating-map');
  const expandBtn = document.getElementById('map-expand-btn');

  function resizeMap() {
    if (!map) return;
    map.invalidateSize({ pan: false });
  }

  function toggleMapExpand(e) {
    if (e) {
      e.preventDefault();
      e.stopPropagation();
    }
    mapContainer.classList.toggle('expanded');
    mapContainer.classList.remove('hovered');
    setTimeout(resizeMap, 120);
    setTimeout(resizeMap, 340);
  }

  if (expandBtn) {
    expandBtn.addEventListener('click', toggleMapExpand);
    expandBtn.addEventListener('touchend', toggleMapExpand);
  }

  // Smooth hover on desktop (only devices with fine pointer and hover capability)
  if (window.matchMedia && window.matchMedia('(hover: hover) and (pointer: fine)').matches) {
    mapContainer.addEventListener('mouseenter', () => {
      if (!mapContainer.classList.contains('expanded')) {
        mapContainer.classList.add('hovered');
        setTimeout(resizeMap, 120);
        setTimeout(resizeMap, 340);
      }
    });

    mapContainer.addEventListener('mouseleave', () => {
      if (!mapContainer.classList.contains('expanded')) {
        mapContainer.classList.remove('hovered');
        setTimeout(resizeMap, 120);
        setTimeout(resizeMap, 340);
      }
    });
  }

  // Recalculate dimensions on transition completion
  mapContainer.addEventListener('transitionend', (e) => {
    if (e.propertyName === 'width' || e.propertyName === 'height') {
      resizeMap();
      updateCountryLabels();
    }
  });

  map.on('click', mapClickHandler);
}

function placeGuessPin(lat, lng) {
  const icon = L.divIcon({
    className: '',
    html: `<div class="guess-marker"></div>`,
    iconAnchor: [12, 12]
  });

  if (guessMarker) map.removeLayer(guessMarker);

  guessMarker = L.marker([lat, lng], { icon }).addTo(map)
    .bindTooltip('Your guess');

  if (canGuess && !isSubmittingGuess) {
    document.getElementById('guess-btn').disabled = false;
  }
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
  roundLocations = [];

  document.getElementById('running-score').textContent = 0;
  document.getElementById('final-overlay').style.display = 'none';

  try {
    roundLocations = await api('/location/batch?count=5');
    await loadRound();
  } catch (e) {
    document.getElementById('img-loader').textContent = 'Failed to load locations: ' + e.message;
  }
}

async function loadRound() {
  canGuess = true;
  isSubmittingGuess = false;

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
    const data = roundLocations[roundNum - 1];

    if (!data) {
      throw new Error(`No location available for round ${roundNum}`);
    }

    currentLocationId = data.id;

    const img = document.getElementById('location-img');

    img.onload = () => {
      document.getElementById('img-loader').style.display = 'none';
      img.style.display = 'block';
      startTimer();
    };

    img.onerror = () => {
      document.getElementById('img-loader').innerHTML =
        '<span style="color:#e94560;">Failed to load location image</span><br>' +
        '<span style="font-size:0.8rem;">URL: ' + data.imageUrl.substring(0, 100) + '...</span>';
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
  if (!guessMarker || isSubmittingGuess || !canGuess) return;

  const currentToken = localStorage.getItem('token') || token;
  if (!currentToken || currentToken === 'null' || currentToken === 'undefined') {
    alert('Please log in before submitting your guess.');
    logout();
    return;
  }

  const guessBtn = document.getElementById('guess-btn');
  isSubmittingGuess = true;
  canGuess = false;
  if (guessBtn) {
    guessBtn.disabled = true;
  }

  const { lat, lng } = guessMarker.getLatLng();

  stopTimer();

  try {
    const data = await api(
      '/game/guess',
      'POST',
      {
        locationId: currentLocationId,
        guessLatitude: lat,
        guessLongitude: lng,
        timeTakenSeconds: elapsedSeconds
      }
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
        document.getElementById('result-ribbon').style.display = 'flex';
    }, 350);

    const actualIcon = L.divIcon({
      className: '',
      html: `<div class="actual-marker"></div>`,
      iconAnchor: [11, 11]
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

    if (guessBtn) {
      guessBtn.disabled = true;
      guessBtn.style.display = 'none';
    }

    if (roundNum >= TOTAL_ROUNDS) {
      setTimeout(showFinal, 1500);
    } else {
      document.getElementById('next-btn').style.display = 'block';
    }
  } catch (e) {
    // If request fails, allow retry
    isSubmittingGuess = false;
    canGuess = true;
    if (guessBtn) {
      guessBtn.disabled = false;
    }
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

  const gradeEl = document.getElementById('final-grade');
  if (gradeEl) {
    if (runningScore >= 24000) {
      gradeEl.textContent = '🎖️ Geography Grandmaster! World-class navigation.';
    } else if (runningScore >= 20000) {
      gradeEl.textContent = '🌟 Master Explorer! Incredible global knowledge.';
    } else if (runningScore >= 15000) {
      gradeEl.textContent = '🧭 Seasoned Traveler! Great geographical instincts.';
    } else if (runningScore >= 10000) {
      gradeEl.textContent = '🗺️ Global Tourist! Solid effort, keep exploring.';
    } else {
      gradeEl.textContent = '🎒 Novice Wanderer! Practice makes perfect.';
    }
  }

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

  const storedToken = localStorage.getItem('token') || token;
  if (storedToken && storedToken !== 'null' && storedToken !== 'undefined') {
    headers['Authorization'] = 'Bearer ' + storedToken;
  }

  const res = await fetch(API + path, {
    method,
    headers,
    body: body ? JSON.stringify(body) : null
  });

  const text = await res.text();

  if (res.status === 401 || res.status === 403) {
    if (path !== '/auth/login' && path !== '/auth/register') {
      logout();
      throw new Error('Session expired or unauthorized. Please log in again.');
    }
  }

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