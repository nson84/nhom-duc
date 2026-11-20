import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import vm from 'node:vm';
import ts from 'typescript';

function load(storage = new Map()) {
  const source = readFileSync(new URL('../src/features/auth/login-memory.ts', import.meta.url), 'utf8');
  const js = ts.transpileModule(source, { compilerOptions: { module: ts.ModuleKind.CommonJS } }).outputText;
  const exports = {};
  vm.runInNewContext(js, { exports, localStorage: {
    getItem: key => storage.get(key) ?? null,
    setItem: (key, value) => storage.set(key, value),
  } });
  return exports;
}

test('logout keeps the successful account and password in this tab only', () => {
  const storage = new Map();
  const memory = load(storage);
  memory.rememberLogin('xuong', 'test-secret');
  assert.equal(memory.getLastLogin().username, 'xuong');
  assert.equal(memory.getLastLogin().password, 'test-secret');
  assert.equal([...storage.values()].includes('test-secret'), false);
  const reloaded = load(storage);
  assert.equal(reloaded.getLastLogin().username, 'xuong');
  assert.equal(reloaded.getLastLogin().password, '');
  assert.equal(memory.passwordForAccount('admin'), '');
  assert.equal(memory.passwordForAccount('xuong'), 'test-secret');
});

test('invalid saved account falls back and changing password drops stale secret', () => {
  const memory = load(new Map([['aluminum.last-login', 'unknown']]));
  assert.equal(memory.getLastLogin().username, 'admin');
  memory.rememberLogin('phanphoi', 'old-secret');
  memory.forgetLoginPassword();
  assert.equal(memory.getLastLogin().username, 'phanphoi');
  assert.equal(memory.getLastLogin().password, '');
});

test('an existing factory session stays selected after logout without a new login', () => {
  const storage = new Map([['aluminum.session', JSON.stringify({ username: 'xuong', role: 'MANUFACTURING', token: 'test' })]]);
  const memory = load(storage);
  const source = readFileSync(new URL('../src/features/auth/auth-store.ts', import.meta.url), 'utf8');
  const js = ts.transpileModule(source, { compilerOptions: { module: ts.ModuleKind.CommonJS } }).outputText;
  const exports = {};
  vm.runInNewContext(js, {
    exports,
    require: name => name === 'react' ? {} : memory,
    localStorage: { getItem: key => storage.get(key) ?? null, setItem: (key, value) => storage.set(key, value), removeItem: key => storage.delete(key) },
    sessionStorage: { getItem: () => null, removeItem: () => {} },
    window: { addEventListener: () => {} },
  });
  exports.authStore.clear();
  assert.equal(memory.getLastLogin().username, 'xuong');
  assert.equal(storage.has('aluminum.session'), false);
});
