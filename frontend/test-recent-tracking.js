// Test script for frontend recent location tracking logic
const assert = require('assert');

// Mock sessionStorage in Node
const mockStorage = new Map();
global.sessionStorage = {
  getItem: (key) => mockStorage.get(key) || null,
  setItem: (key, val) => mockStorage.set(key, String(val)),
  removeItem: (key) => mockStorage.delete(key),
  clear: () => mockStorage.clear()
};

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
    const updated = Array.from(new Set([...validNewIds, ...current])).slice(0, MAX_RECENT_LOCATIONS);
    sessionStorage.setItem(RECENT_LOCATIONS_STORAGE_KEY, JSON.stringify(updated));
  } catch (err) {
    console.warn('Failed to store recent locations in sessionStorage:', err);
  }
}

console.log('=== TESTING FRONTEND RECENT LOCATION TRACKING ===\n');

// 1. Initial empty storage
sessionStorage.clear();
assert.deepStrictEqual(getRecentLocationIds(), [], 'Initial storage must return empty array');
console.log('✅ 1. Initial empty storage test passed');

// 2. Record batch of 5 IDs
recordRecentLocationIds([1, 2, 3, 4, 5]);
assert.deepStrictEqual(getRecentLocationIds(), [1, 2, 3, 4, 5], 'Must record 5 IDs');
console.log('✅ 2. Record initial batch test passed');

// 3. Record second batch with overlapping ID
recordRecentLocationIds([5, 6, 7, 8, 9]);
assert.deepStrictEqual(
  getRecentLocationIds(),
  [5, 6, 7, 8, 9, 1, 2, 3, 4],
  'Must prepend new IDs and deduplicate overlapping ID 5'
);
console.log('✅ 3. Deduplication and prepend order test passed');

// 4. Cap at MAX_RECENT_LOCATIONS (100)
const manyIds = Array.from({ length: 120 }, (_, i) => i + 100);
recordRecentLocationIds(manyIds);
const capped = getRecentLocationIds();
assert.strictEqual(capped.length, 100, 'Must cap total stored locations at 100');
assert.strictEqual(capped[0], 100, 'First element should be the newest ID');
console.log('✅ 4. Maximum limit capping at 100 passed');

// 5. Recovery from corrupted/invalid JSON in sessionStorage
sessionStorage.setItem(RECENT_LOCATIONS_STORAGE_KEY, '{invalid_json');
const recovered = getRecentLocationIds();
assert.deepStrictEqual(recovered, [], 'Must return empty array and reset corrupted JSON');
assert.strictEqual(sessionStorage.getItem(RECENT_LOCATIONS_STORAGE_KEY), null, 'Must remove corrupted key');
console.log('✅ 5. Corrupted JSON recovery passed');

// 6. Graceful handling of invalid values
sessionStorage.setItem(RECENT_LOCATIONS_STORAGE_KEY, JSON.stringify([1, 'abc', null, -5, 2.5, 42]));
const filtered = getRecentLocationIds();
assert.deepStrictEqual(filtered, [1, 42], 'Must only retain positive integer IDs');
console.log('✅ 6. Invalid datatype filtering passed');

console.log('\n🎉 ALL FRONTEND TESTS PASSED SUCCESSFULLY');
