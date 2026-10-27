// Test script for frontend authentication, URL fragment extraction, and email verification logic
const assert = require('assert');

console.log('=== TESTING FRONTEND AUTH & EMAIL VERIFICATION ===\n');

// 1. URL Fragment Extraction and Sanitization
function extractAndSanitizeVerifyToken(locationMock, historyMock) {
  let verifyToken = null;
  const hash = locationMock.hash || '';
  if (hash.includes('verify-token=')) {
    const match = hash.match(/verify-token=([^&]+)/);
    if (match && match[1]) {
      verifyToken = decodeURIComponent(match[1]);
    }
  } else {
    const search = locationMock.search || '';
    if (search.includes('verify-token=')) {
      const match = search.match(/verify-token=([^&]+)/);
      if (match && match[1]) {
        verifyToken = decodeURIComponent(match[1]);
      }
    } else if (search.includes('token=') && locationMock.pathname.includes('verify')) {
      const match = search.match(/token=([^&]+)/);
      if (match && match[1]) {
        verifyToken = decodeURIComponent(match[1]);
      }
    }
  }

  if (verifyToken && historyMock && historyMock.replaceState) {
    const cleanSearch = (locationMock.search || '').replace(/[?&]verify-token=[^&]+/, '').replace(/[?&]token=[^&]+/, '');
    const cleanUrl = locationMock.pathname + (cleanSearch ? (cleanSearch.startsWith('?') ? cleanSearch : '?' + cleanSearch) : '');
    historyMock.replaceState(null, 'GeoGuesser', cleanUrl || '/');
  }

  return verifyToken;
}

// Test 1.1: URL Fragment extraction
let locationMock = { hash: '#verify-token=a1b2c3d4e5f6', search: '', pathname: '/' };
let historyUrl = null;
let historyMock = {
  replaceState: (state, title, url) => { historyUrl = url; }
};
const token1 = extractAndSanitizeVerifyToken(locationMock, historyMock);
assert.strictEqual(token1, 'a1b2c3d4e5f6', 'Must extract raw token from URL fragment');
assert.strictEqual(historyUrl, '/', 'Must immediately wipe fragment from URL via replaceState');
console.log('✅ 1.1. URL fragment token extraction and address bar cleaning passed');

// Test 1.2: URL Fragment with other hash components
locationMock = { hash: '#section=intro&verify-token=my-secret-token-321&ref=email', search: '', pathname: '/index.html' };
const token2 = extractAndSanitizeVerifyToken(locationMock, historyMock);
assert.strictEqual(token2, 'my-secret-token-321', 'Must correctly extract token from multi-param fragment');
assert.strictEqual(historyUrl, '/index.html', 'Must reset URL to clean pathname');
console.log('✅ 1.2. Multi-param URL fragment extraction passed');

// Test 1.3: Query parameter fallback
locationMock = { hash: '', search: '?verify-token=query-param-token-123', pathname: '/' };
const token3 = extractAndSanitizeVerifyToken(locationMock, historyMock);
assert.strictEqual(token3, 'query-param-token-123', 'Must support query parameter fallback');
assert.strictEqual(historyUrl, '/', 'Must strip query param token from URL');
console.log('✅ 1.3. Query param fallback extraction and sanitization passed');


// 2. Client-side Input Validation
function validateRegistration(username, email, password) {
  if (!username || !email || !password) return { valid: false, error: 'Please fill all fields' };
  if (username.length < 3 || username.length > 30) {
    return { valid: false, error: 'Username must be between 3 and 30 characters' };
  }
  const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
  if (!emailRegex.test(email)) {
    return { valid: false, error: 'Please enter a valid email address' };
  }
  if (password.length < 6) {
    return { valid: false, error: 'Password must be at least 6 characters' };
  }
  return { valid: true };
}

assert.strictEqual(validateRegistration('ab', 'test@test.com', '123456').valid, false);
assert.strictEqual(validateRegistration('validuser', 'bad-email', '123456').valid, false);
assert.strictEqual(validateRegistration('validuser', 'test@test.com', '123').valid, false);
assert.strictEqual(validateRegistration('validuser', 'test@test.com', '123456').valid, true);
console.log('✅ 2. Registration client-side validation passed');


// 3. UI State Transitions for Email Verification
class MockDOM {
  constructor() {
    this.elements = {};
  }
  registerElement(id, initialDisplay = 'none') {
    this.elements[id] = {
      id,
      style: { display: initialDisplay },
      textContent: '',
      disabled: false,
      value: ''
    };
    return this.elements[id];
  }
  getElementById(id) {
    return this.elements[id] || this.registerElement(id);
  }
}

const dom = new MockDOM();
const iconEl = dom.registerElement('verify-icon');
const titleEl = dom.registerElement('verify-title');
const descEl = dom.registerElement('verify-desc');
const actionBtn = dom.registerElement('verify-action-btn');
const resendBtn = dom.registerElement('verify-resend-btn');

function renderVerificationState(state, message) {
  if (state === 'LOADING') {
    iconEl.textContent = '⏳';
    titleEl.textContent = 'Verifying Email...';
    descEl.textContent = 'Please wait while we confirm your email address.';
    actionBtn.style.display = 'none';
    resendBtn.style.display = 'none';
  } else if (state === 'SUCCESS') {
    iconEl.textContent = '✅';
    titleEl.textContent = 'Email Verified!';
    descEl.textContent = message || 'Your email has been verified successfully. You can now log in to GeoGuesser.';
    actionBtn.style.display = 'inline-block';
    actionBtn.textContent = 'Continue to Login';
    resendBtn.style.display = 'none';
  } else if (state === 'FAILURE') {
    iconEl.textContent = '❌';
    titleEl.textContent = 'Verification Failed';
    descEl.textContent = message || 'The verification link is invalid or has expired.';
    actionBtn.style.display = 'inline-block';
    actionBtn.textContent = 'Back to Login';
    resendBtn.style.display = 'inline-block';
  }
}

// Test Loading State
renderVerificationState('LOADING');
assert.strictEqual(iconEl.textContent, '⏳');
assert.strictEqual(actionBtn.style.display, 'none');
assert.strictEqual(resendBtn.style.display, 'none');
console.log('✅ 3.1. Verification loading state passed');

// Test Success State
renderVerificationState('SUCCESS');
assert.strictEqual(iconEl.textContent, '✅');
assert.strictEqual(titleEl.textContent, 'Email Verified!');
assert.strictEqual(actionBtn.style.display, 'inline-block');
assert.strictEqual(actionBtn.textContent, 'Continue to Login');
assert.strictEqual(resendBtn.style.display, 'none');
console.log('✅ 3.2. Verification success state passed');

// Test Failure State
renderVerificationState('FAILURE', 'This verification link has expired');
assert.strictEqual(iconEl.textContent, '❌');
assert.strictEqual(titleEl.textContent, 'Verification Failed');
assert.strictEqual(descEl.textContent, 'This verification link has expired');
assert.strictEqual(actionBtn.style.display, 'inline-block');
assert.strictEqual(actionBtn.textContent, 'Back to Login');
assert.strictEqual(resendBtn.style.display, 'inline-block');
console.log('✅ 3.3. Verification failure and expired states passed');


// 4. Double Submission and Button Loading Guards
let isSubmitting = false;
let callCount = 0;
async function mockSubmit(action) {
  if (isSubmitting) return 'BLOCKED';
  isSubmitting = true;
  try {
    callCount++;
    return await action();
  } finally {
    isSubmitting = false;
  }
}

let resolveAction;
const pendingPromise = new Promise(resolve => { resolveAction = resolve; });
mockSubmit(() => pendingPromise);

// Concurrent attempt must be blocked
const concurrentResult = mockSubmit(() => Promise.resolve('ok'));
concurrentResult.then(res => {
  assert.strictEqual(res, 'BLOCKED', 'Concurrent request must be blocked');
  resolveAction('done');
  console.log('✅ 4. Double submission guard passed');
});


// 5. Cooldown Timer Simulation
let cooldownRemaining = 0;
const submitBtn = dom.registerElement('resend-submit-btn');

function simulateCooldownTick() {
  if (cooldownRemaining > 0) {
    cooldownRemaining--;
    if (cooldownRemaining > 0) {
      submitBtn.disabled = true;
      submitBtn.textContent = `Resend in ${cooldownRemaining}s`;
    } else {
      submitBtn.disabled = false;
      submitBtn.textContent = 'Send Verification Email';
    }
  }
}

cooldownRemaining = 3;
submitBtn.disabled = true;
submitBtn.textContent = `Resend in ${cooldownRemaining}s`;

simulateCooldownTick(); // 2s left
assert.strictEqual(cooldownRemaining, 2);
assert.strictEqual(submitBtn.disabled, true);
assert.strictEqual(submitBtn.textContent, 'Resend in 2s');

simulateCooldownTick(); // 1s left
assert.strictEqual(cooldownRemaining, 1);
assert.strictEqual(submitBtn.disabled, true);
assert.strictEqual(submitBtn.textContent, 'Resend in 1s');

simulateCooldownTick(); // 0s left
assert.strictEqual(cooldownRemaining, 0);
assert.strictEqual(submitBtn.disabled, false);
assert.strictEqual(submitBtn.textContent, 'Send Verification Email');
console.log('✅ 5. Resend cooldown simulation passed\n');

console.log('🎉 ALL FRONTEND AUTH & VERIFICATION TESTS PASSED!');
