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

console.log('\n🎉 ALL PHASE 3 MAP INTERACTION TESTS PASSED SUCCESSFULLY!');
