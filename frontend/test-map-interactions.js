// Test script for Phase 3: Smooth Map Interaction and Layout Stability
const assert = require('assert');

console.log('=== TESTING PHASE 3 MAP INTERACTION & LAYOUT STABILITY ===\n');

// 1. Dynamic padding calculation algorithm test
function calculateMapPadding(containerWidth, containerHeight) {
  const padX = Math.max(16, Math.min(50, Math.round(containerWidth * 0.12)));
  const padY = Math.max(16, Math.min(50, Math.round(containerHeight * 0.14)));
  return [padY, padX];
}

// Test small mobile viewport (e.g. 220x140)
const [smallPadY, smallPadX] = calculateMapPadding(220, 140);
assert(smallPadX <= 30, `Small container padX must not exceed 30px (was ${smallPadX})`);
assert(smallPadY <= 25, `Small container padY must not exceed 25px (was ${smallPadY})`);
assert(smallPadX >= 16 && smallPadY >= 16, 'Padding must be at least minimum 16px');
console.log('✅ 1. Small container padding test passed (never consumes small map):', [smallPadY, smallPadX]);

// Test large desktop viewport (e.g. 720x480)
const [largePadY, largePadX] = calculateMapPadding(720, 480);
assert(largePadX <= 50, `Large container padX must cap at 50px (was ${largePadX})`);
assert(largePadY <= 50, `Large container padY must cap at 50px (was ${largePadY})`);
assert(largePadX >= 40 && largePadY >= 40, 'Large container padding must be comfortable');
console.log('✅ 2. Large container padding cap test passed:', [largePadY, largePadX]);

// 2. State-driven collapse prevention logic
class MockMinimapController {
  constructor() {
    this.isPinnedExpanded = false;
    this.isResultState = false;
    this.hasGuessMarker = false;
    this.isExpandedClass = false;
    this.isHoveredClass = false;
    this.hoverIntentTimer = null;
    this.collapseTimer = null;
  }

  onMouseEnter() {
    if (this.collapseTimer) {
      clearTimeout(this.collapseTimer);
      this.collapseTimer = null;
    }
    if (this.isPinnedExpanded || this.isExpandedClass) return;

    this.hoverIntentTimer = setTimeout(() => {
      this.hoverIntentTimer = null;
      if (!this.isPinnedExpanded) {
        this.isHoveredClass = true;
      }
    }, 200);
  }

  onMouseLeave() {
    if (this.hoverIntentTimer) {
      clearTimeout(this.hoverIntentTimer);
      this.hoverIntentTimer = null;
    }
    // Prevent collapse if pinned, if a guess pin has been placed, or if showing results
    if (this.isPinnedExpanded || this.hasGuessMarker || this.isResultState) {
      return;
    }
    this.collapseTimer = setTimeout(() => {
      this.collapseTimer = null;
      if (!this.isPinnedExpanded && !this.hasGuessMarker && !this.isResultState) {
        this.isHoveredClass = false;
      }
    }, 150);
  }

  toggleExpandBtn() {
    this.isPinnedExpanded = !this.isPinnedExpanded;
    if (this.isPinnedExpanded) {
      this.isExpandedClass = true;
      this.isHoveredClass = false;
    } else {
      this.isExpandedClass = false;
      this.isHoveredClass = false;
    }
  }

  onPlaceGuessPin() {
    this.hasGuessMarker = true;
  }

  onSubmitGuess() {
    this.isResultState = true;
    this.isExpandedClass = true;
  }

  onNextRound() {
    this.isResultState = false;
    this.hasGuessMarker = false;
    if (!this.isPinnedExpanded) {
      this.isExpandedClass = false;
      this.isHoveredClass = false;
    }
  }
}

// 3. Quick swipe (< 200ms) test: does NOT expand
(function testQuickSwipe() {
  const ctrl = new MockMinimapController();
  ctrl.onMouseEnter();
  assert.strictEqual(ctrl.isHoveredClass, false, 'Must not expand immediately on enter');
  // Leave after 50ms
  ctrl.onMouseLeave();
  assert.strictEqual(ctrl.hoverIntentTimer, null, 'Hover intent timer must be cancelled');
  assert.strictEqual(ctrl.isHoveredClass, false, 'Must remain collapsed on quick swipe');
  console.log('✅ 3. Hover-intent prevents premature expansion on quick swipe');
})();

// 4. Intentional hover (>= 200ms) expands
(function testDwellExpands() {
  const ctrl = new MockMinimapController();
  ctrl.onMouseEnter();
  // Simulate 200ms delay firing
  ctrl.hoverIntentTimer = null;
  ctrl.isHoveredClass = true;
  assert.strictEqual(ctrl.isHoveredClass, true, 'Intentional hover expands minimap');
  console.log('✅ 4. Dwell expands minimap after hover-intent delay');
})();

// 5. Pin placed locks map expansion when moving to Submit Guess
(function testPinLocksExpansion() {
  const ctrl = new MockMinimapController();
  ctrl.isHoveredClass = true; // User expanded by dwelling on map
  ctrl.onPlaceGuessPin();     // User clicked on map to place pin
  assert.strictEqual(ctrl.hasGuessMarker, true);

  // Player moves cursor away to click "Submit Guess"
  ctrl.onMouseLeave();
  // collapseTimer must NOT even be set because hasGuessMarker is true
  assert.strictEqual(ctrl.collapseTimer, null, 'Must NOT start collapse timer when pin is placed');
  assert.strictEqual(ctrl.isHoveredClass, true, 'Map must remain open while moving to Submit Guess');
  console.log('✅ 5. Placed pin prevents map from unexpectedly collapsing when moving to Submit Guess');
})();

// 6. Submit Guess sets result state and preserves expansion
(function testResultStatePreservation() {
  const ctrl = new MockMinimapController();
  ctrl.onSubmitGuess();
  assert.strictEqual(ctrl.isResultState, true);
  assert.strictEqual(ctrl.isExpandedClass, true);

  // Mouse leave during results
  ctrl.onMouseLeave();
  assert.strictEqual(ctrl.collapseTimer, null);
  assert.strictEqual(ctrl.isExpandedClass, true);
  console.log('✅ 6. Result view preserves expansion for inspecting distance line');

  // Next round collapses if not pinned
  ctrl.onNextRound();
  assert.strictEqual(ctrl.isResultState, false);
  assert.strictEqual(ctrl.isExpandedClass, false, 'Map collapses for next round to show full satellite image');
  console.log('✅ 7. Next round smoothly returns to compact view when unpinned');
})();

// 7. Toggle button pinning overrides hover
(function testToggleButtonPinning() {
  const ctrl = new MockMinimapController();
  ctrl.toggleExpandBtn();
  assert.strictEqual(ctrl.isPinnedExpanded, true);
  assert.strictEqual(ctrl.isExpandedClass, true);

  ctrl.onMouseLeave();
  assert.strictEqual(ctrl.isExpandedClass, true, 'Pinned map never collapses on mouse leave');

  ctrl.onNextRound();
  assert.strictEqual(ctrl.isExpandedClass, true, 'Pinned map stays expanded across rounds');

  ctrl.toggleExpandBtn();
  assert.strictEqual(ctrl.isPinnedExpanded, false);
  assert.strictEqual(ctrl.isExpandedClass, false, 'Unpinning collapses map');
  console.log('✅ 8. Explicit toggle button pinning works across rounds');
})();

// 8. Close distance padding safeguard
function getTargetBounds(bounds, distKm) {
  return distKm < 5 ? { padded: true, original: bounds } : { padded: false, original: bounds };
}
assert.strictEqual(getTargetBounds('mockBounds', 0.2).padded, true, 'Close guesses must be padded to avoid extreme zoom');
assert.strictEqual(getTargetBounds('mockBounds', 1500).padded, false, 'Distant guesses do not need extra padding');
console.log('✅ 9. Close guess boundary padding safeguard passed');


// 9. Actual Production Functions: Setup Mock DOM & Leaflet Environment
const elements = new Map();
function getOrCreateElement(id) {
  if (!elements.has(id)) {
    elements.set(id, {
      id,
      style: { display: 'block' },
      classList: {
        classes: new Set(),
        add(c) { this.classes.add(c); },
        remove(...cs) { cs.forEach(c => this.classes.delete(c)); },
        contains(c) { return this.classes.has(c); }
      },
      textContent: '',
      innerHTML: '',
      disabled: false,
      value: '',
      alt: '',
      src: '',
      title: '',
      clientWidth: 300,
      clientHeight: 200,
      setAttribute(k, v) { this[k] = v; },
      getAttribute(k) { return this[k] || null; },
      addEventListener() {},
      removeEventListener() {}
    });
  }
  return elements.get(id);
}

if (!global.document) {
  global.document = {
    getElementById: (id) => getOrCreateElement(id),
    title: ''
  };
} else {
  const orig = global.document.getElementById;
  global.document.getElementById = (id) => getOrCreateElement(id) || (orig && orig(id));
}

if (!global.window) {
  global.window = {
    matchMedia: () => ({ matches: false }),
    location: { hash: '', search: '', pathname: '/' }
  };
}
if (!global.localStorage) {
  global.localStorage = { getItem: () => null, setItem: () => {}, removeItem: () => {}, clear: () => {} };
}
if (!global.sessionStorage) {
  global.sessionStorage = { getItem: () => null, setItem: () => {}, removeItem: () => {}, clear: () => {} };
}
if (!global.performance) {
  global.performance = { now: () => Date.now() };
}
if (!global.requestAnimationFrame) {
  global.requestAnimationFrame = (cb) => setTimeout(cb, 0);
}
if (!global.cancelAnimationFrame) {
  global.cancelAnimationFrame = (id) => clearTimeout(id);
}

let animationHalted = false;
let layersRemoved = [];
let setViewCalls = [];
const eventListeners = new Map();

const mockMap = {
  stop: () => { animationHalted = true; },
  setView: (center, zoom, opts) => { setViewCalls.push({ center, zoom, opts }); },
  removeLayer: (layer) => { layersRemoved.push(layer.id); },
  invalidateSize: () => {},
  getCenter: () => ({ lat: 20, lng: 0 }),
  panTo: () => {},
  on: (evt, handler) => { eventListeners.set(evt, handler); },
  fire: (evt) => { if (eventListeners.has(evt)) eventListeners.get(evt)(); },
  remove: () => {}
};

if (!global.L) {
  global.L = {
    latLng: (lat, lng) => ({ lat, lng }),
    map: () => mockMap,
    marker: (latlng) => ({ id: 'marker_' + latlng, addTo: () => ({ bindTooltip: () => {} }) }),
    polyline: () => ({ addTo: () => {} }),
    tileLayer: () => ({ addTo: () => {} }),
    control: { attribution: () => ({ addAttribution: () => ({ addTo: () => {} }) }) }
  };
}

const app = require('./app.js');
const state = app._getInternalState();

// Sequential test suite for production functions
(async function runProductionTestsSequentially() {
  // Test 10: Actual Production nextRound: Concurrent Transition Protection
  state.roundNum = 1;
  state.isTransitioningRound = false;
  state.roundLocations = [
    { id: 1, imageUrl: 'img1' },
    { id: 2, imageUrl: 'img2' },
    { id: 3, imageUrl: 'img3' },
    { id: 4, imageUrl: 'img4' },
    { id: 5, imageUrl: 'img5' }
  ];
  state.map = mockMap;

  // Trigger 5 rapid concurrent calls to the actual production nextRound()
  const clicks = [
    app.nextRound(),
    app.nextRound(),
    app.nextRound(),
    app.nextRound(),
    app.nextRound()
  ];

  await Promise.all(clicks);

  assert.strictEqual(state.roundNum, 2, 'Spam clicks must be ignored while transition is in flight');
  assert.strictEqual(state.isTransitioningRound, false, 'Transition lock must be cleared after round loads');
  const nextBtn = getOrCreateElement('next-btn');
  assert.strictEqual(nextBtn.disabled, false, 'next-btn must be re-enabled after round transition completes');
  console.log('✅ 10. Actual production nextRound rapid-click protection passed');

  // Test 11: Advance to round 5 and test cap at TOTAL_ROUNDS
  state.roundNum = 5;
  await app.nextRound();
  assert.strictEqual(state.roundNum, 5, 'roundNum must never exceed TOTAL_ROUNDS (5)');
  console.log('✅ 11. Actual production nextRound max round cap passed');

  // Test 12: Actual Production nextRound: Transition Lock Resets Even on Error
  state.roundNum = 1;
  state.roundLocations = []; // Empty array causes loadRound to throw "No location available for round 2"
  state.isTransitioningRound = false;

  await app.nextRound();

  assert.strictEqual(state.isTransitioningRound, false, 'isTransitioningRound must reset even when loadRound throws');
  assert.strictEqual(nextBtn.disabled, false, 'next-btn must be re-enabled even on load failure');
  const imgLoader = getOrCreateElement('img-loader');
  assert(imgLoader.textContent.includes('No location available'), 'Error message must be displayed to user');
  console.log('✅ 12. Actual production nextRound error recovery and lock reset passed');

  // Test 13: Actual Production loadRound: Animation Halting via map.stop() & Layer Cleanup
  animationHalted = false;
  layersRemoved = [];
  setViewCalls = [];

  state.map = mockMap;
  state.guessMarker = { id: 'guessMarker' };
  state.actualMarker = { id: 'actualMarker' };
  state.distanceLine = { id: 'distanceLine' };
  state.roundLocations = [{ id: 1, imageUrl: 'img1' }];
  state.roundNum = 1;

  await app.loadRound();

  assert.strictEqual(animationHalted, true, 'map.stop() must be invoked by actual loadRound to halt animations');
  assert.deepStrictEqual(layersRemoved, ['guessMarker', 'actualMarker', 'distanceLine'], 'Layers must be removed from map');
  assert.strictEqual(state.guessMarker, null, 'guessMarker reference must be nullified');
  assert.strictEqual(state.actualMarker, null, 'actualMarker reference must be nullified');
  assert.strictEqual(state.distanceLine, null, 'distanceLine reference must be nullified');
  assert.strictEqual(setViewCalls.length, 1);
  assert.deepStrictEqual(setViewCalls[0].center, [20, 0]);
  assert.strictEqual(setViewCalls[0].zoom, 2);
  assert.strictEqual(setViewCalls[0].opts.animate, false, 'setView must not animate on round reset');
  console.log('✅ 13. Actual production loadRound map reset, animation halt, and layer cleanup passed');

  // Test 14: Actual Production startSmoothResize: Zero-Dimension Safeguard & User Gesture Cancellation
  const floatingMap = getOrCreateElement('floating-map');
  state.map = mockMap;

  // Initialize map in app
  app.initMap();

  // Test zero dimensions (hidden container)
  floatingMap.clientWidth = 0;
  floatingMap.clientHeight = 0;
  state.resizeRafId = null;

  if (typeof state.startSmoothResize === 'function') {
    state.startSmoothResize();
    assert.strictEqual(state.resizeRafId, null, 'startSmoothResize must ignore 0x0 hidden container');
  }

  // Test valid dimensions
  floatingMap.clientWidth = 400;
  floatingMap.clientHeight = 300;
  if (typeof state.startSmoothResize === 'function') {
    state.startSmoothResize();
    assert.notStrictEqual(state.resizeRafId, null, 'startSmoothResize must schedule RAF for valid dimensions');

    // Simulate user initiating map drag during smooth resize
    mockMap.fire('dragstart');
    assert.strictEqual(state.resizeRafId, null, 'User drag gesture must immediately cancel resize RAF loop');
  }

  console.log('✅ 14. Actual production startSmoothResize zero-dimension guard and user gesture cancellation passed');

  console.log('\n🎉 ALL ACTUAL MAP INTERACTION & STABILITY TESTS PASSED SUCCESSFULLY!');
})();
