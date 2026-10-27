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
let isPinnedExpanded = false;
let isResultState = false;
let startSmoothResize = null;
let hoverIntentTimer = null;
let collapseTimer = null;
let resizeRafId = null;

let isSubmittingLogin = false;
let isSubmittingRegister = false;
let isSubmittingResend = false;
let resendCooldownInterval = null;
let resendCooldownRemaining = 0;

// ── INIT ──────────────────────────────────────────────────
window.onload = () => {
  // Check for email verification token in URL fragment (#verify-token=...) or query param
  let verifyToken = null;
  const hash = window.location.hash || '';
  if (hash.includes('verify-token=')) {
    const match = hash.match(/verify-token=([^&]+)/);
    if (match && match[1]) {
      verifyToken = decodeURIComponent(match[1]);
    }
  } else {
    const params = new URLSearchParams(window.location.search);
    if (params.has('verify-token')) {
      verifyToken = params.get('verify-token');
    } else if (params.has('token') && (window.location.pathname.includes('verify') || hash.includes('verify'))) {
      verifyToken = params.get('token');
    }
  }

  // Clear token immediately from browser address bar so it is never exposed in history
  if (verifyToken) {
    if (window.history && window.history.replaceState) {
      const cleanSearch = window.location.search.replace(/[?&]verify-token=[^&]+/, '').replace(/[?&]token=[^&]+/, '');
      const cleanUrl = window.location.pathname + (cleanSearch ? (cleanSearch.startsWith('?') ? cleanSearch : '?' + cleanSearch) : '');
      window.history.replaceState(null, document.title, cleanUrl || '/');
    }
    show('auth-screen');
    handleVerificationToken(verifyToken);
    return;
  }

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

function toggleAuthSection(activeId) {
  const sections = ['login-form', 'register-form', 'verify-status-section', 'resend-form'];
  sections.forEach(id => {
    const el = document.getElementById(id);
    if (el) {
      el.style.display = (id === activeId) ? (id === 'verify-status-section' ? 'block' : 'flex') : 'none';
    }
  });
}

function showLogin() {
  document.title = 'GeoGuesser — Sign In';
  show('auth-screen');
  toggleAuthSection('login-form');
  msg('l-msg', '', '');
  const unverifiedNotice = document.getElementById('unverified-login-actions');
  if (unverifiedNotice) unverifiedNotice.style.display = 'none';
  announce('Sign in to GeoGuesser');
}

function showRegister() {
  document.title = 'GeoGuesser — Create Account';
  show('auth-screen');
  toggleAuthSection('register-form');
  msg('r-msg', '', '');
  announce('Create a new GeoGuesser account');
}

function showResendVerification(prefill) {
  document.title = 'GeoGuesser — Resend Verification Link';
  show('auth-screen');
  toggleAuthSection('resend-form');
  msg('resend-msg', '', '');
  const input = document.getElementById('resend-input');
  if (input && prefill) {
    input.value = prefill;
  }
  announce('Request a new email verification link');
}

async function handleVerificationToken(rawToken) {
  document.title = 'GeoGuesser — Verifying Email';
  show('auth-screen');
  toggleAuthSection('verify-status-section');

  const iconEl = document.getElementById('verify-icon');
  const titleEl = document.getElementById('verify-title');
  const descEl = document.getElementById('verify-desc');
  const actionBtn = document.getElementById('verify-action-btn');
  const resendBtn = document.getElementById('verify-resend-btn');

  if (iconEl) iconEl.textContent = '⏳';
  if (titleEl) titleEl.textContent = 'Verifying Email...';
  if (descEl) descEl.textContent = 'Please wait while we confirm your email address.';
  if (actionBtn) actionBtn.style.display = 'none';
  if (resendBtn) resendBtn.style.display = 'none';
  announce('Verifying email address, please wait');

  try {
    const res = await api('/auth/verify', 'POST', { token: rawToken });
    document.title = 'GeoGuesser — Email Verified!';
    if (iconEl) iconEl.textContent = '✅';
    if (titleEl) titleEl.textContent = 'Email Verified!';
    if (descEl) descEl.textContent = res.message || 'Your email has been verified successfully. You can now log in to GeoGuesser.';
    if (actionBtn) {
      actionBtn.style.display = 'inline-block';
      actionBtn.textContent = 'Continue to Login';
      actionBtn.onclick = () => showLogin();
      actionBtn.focus();
    }
    announce('Email successfully verified. You can now log in.');
  } catch (err) {
    document.title = 'GeoGuesser — Verification Failed';
    if (iconEl) iconEl.textContent = '❌';
    if (titleEl) titleEl.textContent = 'Verification Failed';
    if (descEl) descEl.textContent = err.message || 'The verification link is invalid or has expired.';
    if (actionBtn) {
      actionBtn.style.display = 'inline-block';
      actionBtn.textContent = 'Back to Login';
      actionBtn.onclick = () => showLogin();
    }
    if (resendBtn) {
      resendBtn.style.display = 'inline-block';
      resendBtn.focus();
    }
    announce('Verification failed: ' + (err.message || 'Link invalid or expired'));
  }
}

function showGame() {
  document.title = `GeoGuesser — Round ${roundNum} of ${TOTAL_ROUNDS}`;
  show('game-screen');
  initMap();
}

function showLeaderboard() {
  document.title = 'GeoGuesser — Global Leaderboard';
  show('lb-screen');
  loadLeaderboard();
  announce('Global Leaderboard loaded');
}

function showHistory() {
  document.title = 'GeoGuesser — Game History';
  show('history-screen');
  loadHistory();
  announce('Recent game history loaded');
}

function handleBrandClick(event) {
  if (event) event.preventDefault();
  const storedToken = localStorage.getItem('token') || token;
  if (storedToken && storedToken !== 'null' && storedToken !== 'undefined') {
    showGame();
  } else {
    showLogin();
  }
}

function announce(text) {
  const el = document.getElementById('game-status-announcer');
  if (el) {
    el.textContent = text;
  }
}

function showToast(message, type = 'info') {
  const container = document.getElementById('toast-container');
  if (!container) {
    console.log(`[${type}] ${message}`);
    return;
  }
  const toast = document.createElement('div');
  toast.className = `toast toast-${type}`;
  toast.setAttribute('role', 'alert');
  const icon = type === 'error' ? '❌' : (type === 'success' ? '✅' : 'ℹ️');
  toast.innerHTML = `<span class="toast-icon" aria-hidden="true">${icon}</span><span class="toast-body">${escapeHtml(message)}</span>`;
  toast.onclick = () => {
    toast.style.opacity = '0';
    toast.style.transform = 'translateY(-10px)';
    setTimeout(() => toast.remove(), 200);
  };
  container.appendChild(toast);
  announce(message);
  setTimeout(() => {
    if (toast.parentNode) {
      toast.style.opacity = '0';
      toast.style.transform = 'translateY(-10px)';
      setTimeout(() => toast.remove(), 200);
    }
  }, 4500);
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

  const HOVER_INTENT_DELAY = 200; // ms required before desktop hover expansion
  const COLLAPSE_DELAY = 150;     // ms grace period before collapsing on mouseleave

  startSmoothResize = function(targetCenter) {
    if (!map) return;
    if (resizeRafId) {
      cancelAnimationFrame(resizeRafId);
      resizeRafId = null;
    }

    const centerToPreserve = targetCenter
      || (guessMarker ? guessMarker.getLatLng() : map.getCenter());

    const prefersReducedMotion = window.matchMedia && window.matchMedia('(prefers-reduced-motion: reduce)').matches;
    if (prefersReducedMotion) {
      map.invalidateSize({ pan: false });
      if (centerToPreserve) {
        map.panTo(centerToPreserve, { animate: false });
      }
      updateCountryLabels();
      return;
    }

    const duration = 320; // Matches CSS transition duration
    const startTime = performance.now();

    function step(now) {
      if (!map) return;
      map.invalidateSize({ pan: false });
      if (centerToPreserve) {
        map.panTo(centerToPreserve, { animate: false });
      }

      if (now - startTime < duration) {
        resizeRafId = requestAnimationFrame(step);
      } else {
        resizeRafId = null;
        map.invalidateSize({ pan: false });
        if (centerToPreserve) {
          map.panTo(centerToPreserve, { animate: false });
        }
        updateCountryLabels();
      }
    }

    resizeRafId = requestAnimationFrame(step);
  };

  function toggleMapExpand(e) {
    if (e) {
      e.preventDefault();
      e.stopPropagation();
    }
    if (hoverIntentTimer) {
      clearTimeout(hoverIntentTimer);
      hoverIntentTimer = null;
    }
    if (collapseTimer) {
      clearTimeout(collapseTimer);
      collapseTimer = null;
    }

    isPinnedExpanded = !isPinnedExpanded;
    if (isPinnedExpanded) {
      mapContainer.classList.add('expanded');
      mapContainer.classList.remove('hovered');
      if (expandBtn) {
        expandBtn.setAttribute('aria-expanded', 'true');
        expandBtn.title = 'Minimize map';
      }
    } else {
      mapContainer.classList.remove('expanded', 'hovered');
      if (expandBtn) {
        expandBtn.setAttribute('aria-expanded', 'false');
        expandBtn.title = 'Expand map';
      }
    }
    startSmoothResize();
  }

  if (expandBtn) {
    expandBtn.addEventListener('click', toggleMapExpand);
    expandBtn.addEventListener('touchend', toggleMapExpand);
  }

  // Smooth hover intent on desktop
  if (window.matchMedia && window.matchMedia('(hover: hover) and (pointer: fine)').matches) {
    mapContainer.addEventListener('mouseenter', () => {
      if (collapseTimer) {
        clearTimeout(collapseTimer);
        collapseTimer = null;
      }
      if (isPinnedExpanded || mapContainer.classList.contains('expanded')) {
        return;
      }
      hoverIntentTimer = setTimeout(() => {
        hoverIntentTimer = null;
        if (!isPinnedExpanded) {
          mapContainer.classList.add('hovered');
          startSmoothResize();
        }
      }, HOVER_INTENT_DELAY);
    });

    mapContainer.addEventListener('mouseleave', () => {
      if (hoverIntentTimer) {
        clearTimeout(hoverIntentTimer);
        hoverIntentTimer = null;
      }
      // Never collapse unexpectedly if pinned, if a guess pin has been placed, or if viewing results
      if (isPinnedExpanded || guessMarker !== null || isResultState) {
        return;
      }
      collapseTimer = setTimeout(() => {
        collapseTimer = null;
        if (!isPinnedExpanded && guessMarker === null && !isResultState) {
          mapContainer.classList.remove('hovered');
          startSmoothResize();
        }
      }, COLLAPSE_DELAY);
    });
  }

  // ResizeObserver fallback to keep Leaflet synchronized with container changes
  if (typeof ResizeObserver !== 'undefined') {
    let lastW = mapContainer.clientWidth;
    let lastH = mapContainer.clientHeight;
    const ro = new ResizeObserver(entries => {
      for (const entry of entries) {
        const { width, height } = entry.contentRect;
        if (Math.abs(width - lastW) > 1 || Math.abs(height - lastH) > 1) {
          lastW = width;
          lastH = height;
          if (!resizeRafId && map) {
            map.invalidateSize({ pan: false });
            updateCountryLabels();
          }
        }
      }
    });
    ro.observe(mapContainer);
  }

  mapContainer.addEventListener('transitionend', (e) => {
    if (e.propertyName === 'width' || e.propertyName === 'height') {
      if (map) {
        map.invalidateSize({ pan: false });
        updateCountryLabels();
      }
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
  announce(`Pin placed at latitude ${lat.toFixed(2)}, longitude ${lng.toFixed(2)}. Submit Guess button is enabled.`);
}

// ── AUTH ──────────────────────────────────────────────────
async function login() {
  if (isSubmittingLogin) return;
  const u = val('l-user');
  const p = val('l-pass');

  if (!u || !p) return msg('l-msg', 'Fill all fields', 'red');

  const btn = document.getElementById('login-btn');
  const unverifiedNotice = document.getElementById('unverified-login-actions');
  if (unverifiedNotice) unverifiedNotice.style.display = 'none';

  isSubmittingLogin = true;
  if (btn) {
    btn.disabled = true;
    btn.textContent = 'Logging in...';
  }

  try {
    const data = await api('/auth/login', 'POST', {
      username: u,
      password: p
    });

    saveAuth(data);
    showGame();
    startGame();
  } catch (e) {
    if (e.code === 'ACCOUNT_NOT_VERIFIED' || (e.message && e.message.toLowerCase().includes('not verified'))) {
      msg('l-msg', 'Account is not verified. Please check your email inbox or request a new verification link below.', 'red');
      if (unverifiedNotice) unverifiedNotice.style.display = 'block';
    } else {
      msg('l-msg', e.message, 'red');
    }
  } finally {
    isSubmittingLogin = false;
    if (btn) {
      btn.disabled = false;
      btn.textContent = 'Login';
    }
  }
}

async function register() {
  if (isSubmittingRegister) return;
  const u = val('r-user');
  const e = val('r-email');
  const p = val('r-pass');

  if (!u || !e || !p) return msg('r-msg', 'Please fill all fields', 'red');

  if (u.length < 3 || u.length > 30) {
    return msg('r-msg', 'Username must be between 3 and 30 characters', 'red');
  }

  const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
  if (!emailRegex.test(e)) {
    return msg('r-msg', 'Please enter a valid email address', 'red');
  }

  if (p.length < 6) {
    return msg('r-msg', 'Password must be at least 6 characters', 'red');
  }

  const btn = document.getElementById('register-btn');
  isSubmittingRegister = true;
  if (btn) {
    btn.disabled = true;
    btn.textContent = 'Creating Account...';
  }

  try {
    const data = await api('/auth/register', 'POST', {
      username: u,
      email: e,
      password: p
    });

    if (data.verificationRequired) {
      msg('r-msg', data.message || 'Account created! Please check your email to verify before logging in.', 'green');
      const uEl = document.getElementById('r-user');
      const eEl = document.getElementById('r-email');
      const pEl = document.getElementById('r-pass');
      if (uEl) uEl.value = '';
      if (eEl) eEl.value = '';
      if (pEl) pEl.value = '';
      setTimeout(() => {
        showLogin();
        msg('l-msg', 'Verification link sent! Check your inbox, then log in here.', 'green');
      }, 3500);
    } else {
      msg('r-msg', 'Account created!', 'green');
      if (data.token) {
        saveAuth(data);
        showGame();
        startGame();
      } else {
        setTimeout(showLogin, 1200);
      }
    }
  } catch (err) {
    msg('r-msg', err.message, 'red');
  } finally {
    isSubmittingRegister = false;
    if (btn) {
      btn.disabled = false;
      btn.textContent = 'Create Account';
    }
  }
}

async function resendVerification() {
  if (isSubmittingResend || resendCooldownRemaining > 0) return;
  const input = val('resend-input');

  if (!input) return msg('resend-msg', 'Please enter your email or username', 'red');

  const btn = document.getElementById('resend-submit-btn');
  isSubmittingResend = true;
  if (btn) {
    btn.disabled = true;
    btn.textContent = 'Sending...';
  }

  try {
    const res = await api('/auth/resend-verification', 'POST', {
      usernameOrEmail: input
    });

    msg('resend-msg', res.message || 'If an account exists, a verification email has been sent.', 'green');
    startResendCooldown(60);
  } catch (err) {
    msg('resend-msg', err.message, 'red');
    if (err.message && err.message.toLowerCase().includes('cooldown') || err.status === 429) {
      startResendCooldown(60);
    }
  } finally {
    isSubmittingResend = false;
    if (btn && resendCooldownRemaining <= 0) {
      btn.disabled = false;
      btn.textContent = 'Send Verification Email';
    }
  }
}

function startResendCooldown(seconds) {
  if (resendCooldownInterval) {
    clearInterval(resendCooldownInterval);
  }
  resendCooldownRemaining = seconds;
  const btn = document.getElementById('resend-submit-btn');
  const updateBtn = () => {
    if (btn) {
      if (resendCooldownRemaining > 0) {
        btn.disabled = true;
        btn.textContent = `Resend in ${resendCooldownRemaining}s`;
      } else {
        btn.disabled = false;
        btn.textContent = 'Send Verification Email';
      }
    }
  };

  updateBtn();
  resendCooldownInterval = setInterval(() => {
    resendCooldownRemaining--;
    if (resendCooldownRemaining <= 0) {
      clearInterval(resendCooldownInterval);
      resendCooldownInterval = null;
      updateBtn();
    } else {
      updateBtn();
    }
  }, 1000);
}

function logout() {
  localStorage.clear();
  token = playerId = username = null;

  if (resendCooldownInterval) {
    clearInterval(resendCooldownInterval);
    resendCooldownInterval = null;
  }
  resendCooldownRemaining = 0;

  if (hoverIntentTimer) {
    clearTimeout(hoverIntentTimer);
    hoverIntentTimer = null;
  }
  if (collapseTimer) {
    clearTimeout(collapseTimer);
    collapseTimer = null;
  }
  if (resizeRafId) {
    cancelAnimationFrame(resizeRafId);
    resizeRafId = null;
  }
  isPinnedExpanded = false;
  isResultState = false;

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

// ── RECENT LOCATION TRACKING ──────────────────────────────
const RECENT_LOCATIONS_STORAGE_KEY = 'geoguesser_recent_locations';
const MAX_RECENT_LOCATIONS = 100;

function getRecentLocationIds() {
  try {
    const raw = sessionStorage.getItem(RECENT_LOCATIONS_STORAGE_KEY);
    if (!raw) return [];
    const parsed = JSON.parse(raw);
    if (Array.isArray(parsed)) {
      return parsed.filter(id => typeof id === 'number' && Number.isInteger(id) && id > 0);
    }
  } catch (err) {
    console.warn('Failed to parse recent locations from sessionStorage, resetting:', err);
    try {
      sessionStorage.removeItem(RECENT_LOCATIONS_STORAGE_KEY);
    } catch (_) {}
  }
  return [];
}

function recordRecentLocationIds(ids) {
  if (!Array.isArray(ids) || ids.length === 0) return;
  try {
    const validNewIds = ids.filter(id => typeof id === 'number' && Number.isInteger(id) && id > 0);
    if (validNewIds.length === 0) return;
    const current = getRecentLocationIds();
    // Prepend new IDs and deduplicate, keeping up to MAX_RECENT_LOCATIONS
    const updated = Array.from(new Set([...validNewIds, ...current])).slice(0, MAX_RECENT_LOCATIONS);
    sessionStorage.setItem(RECENT_LOCATIONS_STORAGE_KEY, JSON.stringify(updated));
  } catch (err) {
    console.warn('Failed to store recent locations in sessionStorage:', err);
  }
}

// ── GAME ──────────────────────────────────────────────────
async function startGame() {
  roundNum = 1;
  runningScore = 0;
  roundLocations = [];

  document.getElementById('running-score').textContent = 0;
  document.getElementById('final-overlay').style.display = 'none';

  try {
    const recentIds = getRecentLocationIds();
    const query = recentIds.length > 0
      ? `?count=5&excludeIds=${encodeURIComponent(recentIds.join(','))}`
      : '?count=5';

    roundLocations = await api('/location/batch' + query);

    if (Array.isArray(roundLocations)) {
      const returnedIds = roundLocations
        .map(loc => loc && loc.id)
        .filter(id => typeof id === 'number' && Number.isInteger(id) && id > 0);
      recordRecentLocationIds(returnedIds);
    }

    await loadRound();
  } catch (e) {
    document.getElementById('img-loader').textContent = 'Failed to load locations: ' + e.message;
  }
}

async function loadRound() {
  canGuess = true;
  isSubmittingGuess = false;
  isResultState = false;

  const mapContainer = document.getElementById('floating-map');
  if (mapContainer && !isPinnedExpanded) {
    mapContainer.classList.remove('expanded', 'hovered');
    if (typeof startSmoothResize === 'function') {
      startSmoothResize();
    }
  }

  document.getElementById('result-ribbon').style.display = 'none';
  document.getElementById('guess-btn').disabled = true;
  document.getElementById('guess-btn').style.display = 'inline-flex';
  document.getElementById('next-btn').style.display = 'none';

  document.getElementById('round-num').textContent = roundNum;
  document.getElementById('pin-hint').textContent = '📍 Click on map to place guess';
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
    if (img) {
      img.alt = `Satellite view of mystery location for Round ${roundNum} of ${TOTAL_ROUNDS}`;
    }

    document.title = `GeoGuesser — Round ${roundNum} of ${TOTAL_ROUNDS}`;
    announce(`Round ${roundNum} of ${TOTAL_ROUNDS} started. Satellite imagery loading.`);

    img.onload = () => {
      document.getElementById('img-loader').style.display = 'none';
      img.style.display = 'block';
      startTimer();
      announce(`Round ${roundNum} satellite image ready. Place your guess on the world map.`);
    };

    img.onerror = () => {
      console.warn('Satellite imagery failed to load for location ID:', data.id);
      document.getElementById('img-loader').innerHTML =
        '<div style="text-align:center;padding:20px;">' +
        '<span style="color:#e94560;font-weight:600;font-size:1rem;">Satellite imagery unavailable</span><br>' +
        '<span style="font-size:0.85rem;color:var(--text-secondary);margin-top:6px;display:block;">Check network connection or Mapbox API configuration.</span>' +
        '</div>';
      announce('Satellite imagery failed to load for this location.');
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
    showToast('Please log in before submitting your guess.', 'error');
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

    announce(`Guess result: ${data.performanceLabel}. Distance: ${data.distanceKm.toFixed(1)} km from ${data.city}, ${data.country}. Scored ${data.score} points. Running score: ${runningScore} points.`);

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

    isResultState = true;

    // Ensure map is expanded for optimal result inspection
    const mapContainer = document.getElementById('floating-map');
    if (mapContainer && !mapContainer.classList.contains('expanded') && !mapContainer.classList.contains('hovered')) {
      mapContainer.classList.add('expanded');
      if (typeof startSmoothResize === 'function') {
        startSmoothResize();
      }
    }

    const bounds = L.latLngBounds([
      [lat, lng],
      [data.actualLatitude, data.actualLongitude]
    ]);

    const mapEl = document.getElementById('world-map');
    const w = mapEl ? mapEl.clientWidth : 400;
    const h = mapEl ? mapEl.clientHeight : 300;
    // Sensible dynamic padding that never forces extreme zoom-out
    const padX = Math.max(16, Math.min(50, Math.round(w * 0.12)));
    const padY = Math.max(16, Math.min(50, Math.round(h * 0.14)));

    // For very close guesses (< 5 km), apply a small padding to bounds to prevent extreme zoom-in
    const distKm = data.distanceKm || 0;
    const targetBounds = distKm < 5 ? bounds.pad(0.35) : bounds;

    map.flyToBounds(targetBounds, {
      padding: [padY, padX],
      maxZoom: 9,
      minZoom: 2,
      duration: 1.2
    });

    if (guessBtn) {
      guessBtn.disabled = true;
      guessBtn.style.display = 'none';
    }

    if (roundNum >= TOTAL_ROUNDS) {
      setTimeout(showFinal, 1500);
    } else {
      document.getElementById('next-btn').style.display = 'inline-flex';
    }
  } catch (e) {
    // If request fails, allow retry
    isSubmittingGuess = false;
    canGuess = true;
    if (guessBtn) {
      guessBtn.disabled = false;
    }
    showToast(e.message, 'error');
  }
}

function nextRound() {
  roundNum++;
  loadRound();
}

function showFinal() {
  document.getElementById('final-score-val').textContent =
    runningScore.toLocaleString();

  document.title = `GeoGuesser — Game Over (${runningScore.toLocaleString()} pts)`;
  announce(`Game Over! Final score: ${runningScore.toLocaleString()} out of 25,000 points.`);

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
    if (!path.startsWith('/auth/')) {
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
    const error = new Error(data.error || data.message || `HTTP ${res.status}`);
    error.status = res.status;
    error.code = data.code;
    throw error;
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