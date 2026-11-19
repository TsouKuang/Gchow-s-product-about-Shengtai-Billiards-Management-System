const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const { palettes, resolveAppearance } = require('../miniprogram/pages/home/appearance');

function readTemplate(file) {
  return fs
    .readFileSync(file, 'utf8')
    .replace(/<include\s+src="([^"]+)"\s*\/>/g, (_, src) =>
      readTemplate(path.resolve(path.dirname(file), src)),
    );
}

let definition;
let storage = new Map();
let navigation;
global.Page = (value) => {
  definition = value;
};
global.getApp = () => ({ globalData: { loginMode: 'member' } });
global.wx = {
  getStorageSync: (key) => storage.get(key),
  setStorageSync: (key, value) => storage.set(key, value),
  removeStorageSync: (key) => storage.delete(key),
  setNavigationBarColor: (value) => {
    navigation = value;
  },
  setBackgroundColor() {},
  setNavigationBarTitle() {},
  vibrateShort() {},
  pageScrollTo() {},
};
require('../miniprogram/pages/home/home');

function page(overrides = {}) {
  return {
    ...definition,
    data: {
      ...structuredClone(definition.data),
      logged: true,
      customerId: 12,
      membership: { badge: 'gold' },
      ...overrides,
    },
    setData(update) {
      Object.assign(this.data, update);
    },
  };
}
function luminance(hex) {
  const [r, g, b] = hex
    .slice(1)
    .match(/../g)
    .map((x) => parseInt(x, 16) / 255)
    .map((x) => (x <= 0.04045 ? x / 12.92 : ((x + 0.055) / 1.055) ** 2.4));
  return r * 0.2126 + g * 0.7152 + b * 0.0722;
}
function contrast(a, b) {
  const x = luminance(a),
    y = luminance(b);
  return (Math.max(x, y) + 0.05) / (Math.min(x, y) + 0.05);
}

test('all base and exclusive palettes keep readable text and buttons', () => {
  assert.equal(Object.keys(palettes).length, 15);
  for (const key of Object.keys(palettes)) {
    const p = resolveAppearance(key).palette;
    for (const [foreground, background] of [
      ['text', 'surface'],
      ['muted', 'surface'],
      ['muted', 'bg'],
      ['on-accent', 'accent'],
      ['hero-text', 'hero-from'],
      ['hero-text', 'hero-to'],
      ['hero-muted', 'hero-to'],
    ]) {
      assert.ok(
        contrast(p[foreground], p[background]) >= 4.5,
        `${key}: ${foreground} on ${background}`,
      );
    }
  }
});

test('wallet, gallery and ordinary pages retain the same selected palette', () => {
  storage = new Map([['memberExclusiveSkin:12', 'tech']]);
  const p = page();
  const expected = resolveAppearance('gold', 'tech');
  for (const profilePage of [null, 'wallet', 'skins', 'leaderboard', 'details', 'membership']) {
    p.data.profilePage = profilePage;
    p.applySkin();
    assert.equal(p.data.themeStyle, expected.style);
    assert.equal(navigation.backgroundColor, expected.navigation);
  }
});

test('exclusive themes respect rank and remain isolated between accounts', () => {
  storage = new Map([['memberExclusiveSkin:12', 'obsidian']]);
  const p = page();
  p.applySkin();
  assert.equal(p.data.exclusiveSkinKey, 'obsidian');
  p.data.membership = { badge: 'bronze' };
  p.applySkin();
  assert.equal(p.data.exclusiveSkinKey, 'none');
  p.data.customerId = 13;
  p.data.membership = { badge: 'gold' };
  p.applySkin();
  assert.equal(p.data.exclusiveSkinKey, 'none');
});

test('hidden balance persists across refreshes, reopening and account switches', () => {
  storage = new Map();
  const p = page();
  p.applySkin();
  assert.equal(p.data.walletHidden, false);
  p.toggleWalletBalance();
  assert.equal(storage.get('memberWalletHidden:12'), true);
  p.applyWallet({ balance: 123.45, transactions: [] });
  p.applySkin();
  assert.equal(p.data.walletHidden, true);
  assert.equal(p.data.walletBalance, '123.45');
  const reopened = page();
  reopened.applySkin();
  assert.equal(reopened.data.walletHidden, true);
  reopened.data.customerId = 13;
  reopened.applySkin();
  assert.equal(reopened.data.walletHidden, false);
  reopened.data.customerId = 12;
  reopened.applySkin();
  assert.equal(reopened.data.walletHidden, true);
  reopened.toggleWalletBalance();
  assert.equal(storage.get('memberWalletHidden:12'), false);
  reopened.resetAccount();
  assert.equal(reopened.data.walletHidden, true);
  assert.equal(reopened.data.walletBalance, '0.00');
});

test('privacy preference never changes available funds or the insufficient-balance check', async () => {
  storage = new Map([['memberWalletHidden:12', true]]);
  const p = page();
  p.applySkin();
  p.applyWallet({ balance: 10, pinSet: true, transactions: [] });
  p.walletIntent = { amount: 38.8 };
  p.setData({ checkoutMethod: 'WALLET' });
  p.openPayPin = () => assert.fail('Insufficient balance must not open PIN entry');
  await p.confirmCheckout();
  assert.match(p.data.walletError, /余额不足/);
  assert.equal(p.data.wallet.balance, 10);
});

test('all balance displays, including the profile entry and checkout, honor privacy', () => {
  const template = readTemplate(path.join(__dirname, '../miniprogram/pages/home/home.wxml'));
  const displays = template.match(
    /\{\{[^{}]*(?:walletBalance|walletRecord\.balanceText)[^{}]*\}\}/g,
  );
  assert.equal(displays.length, 5);
  for (const display of displays) assert.ok(display.includes('walletHidden?'), display);
});

test('all modal roots receive theme colors independently from the screen', () => {
  const template = readTemplate(path.join(__dirname, '../miniprogram/pages/home/home.wxml'));
  const roots = template.match(
    /<view[^>]+class="app-themed (?:payment-overlay|service-booking-overlay|wallet-overlay|wallet-pin-overlay|club-prompt-overlay)[^"]*"[^>]*>/g,
  );
  assert.equal(roots.length, 10);
  for (const root of roots) assert.ok(root.includes('style="{{themeStyle}}"'), root);
});
