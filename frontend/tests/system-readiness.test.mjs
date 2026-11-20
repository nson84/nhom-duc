import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import vm from 'node:vm';
import ts from 'typescript';

// Run the real polling function with an Axios double and deterministic time, without a browser or extra dependencies.
function loadReadyGate(payload, networkFailure = false) {
  const source = readFileSync(new URL('../src/features/system/system-api.ts', import.meta.url), 'utf8')
    .replaceAll('import.meta.env.VITE_API_BASE_URL', 'undefined');
  const js = ts.transpileModule(source, { compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2022, esModuleInterop: true } }).outputText;
  const exports = {};
  let ticks = 0;
  vm.runInNewContext(js, {
    exports,
    require(name) {
      assert.equal(name, 'axios');
      return { __esModule: true, default: { async get() {
        if (networkFailure) throw new Error('Test network failure');
        return { data: payload };
      } } };
    },
    Date: { now: () => ticks++ < 2 ? 0 : 60001 },
    DOMException,
    window: { setTimeout: callback => { queueMicrotask(callback); return 1; }, clearTimeout() {} },
  });
  return exports.waitUntilReady;
}

test('accepts this application only when backend and MySQL are ready', async () => {
  await loadReadyGate({ success: true, data: { status: 'UP', database: 'UP', application: 'aluminum-manager' } })(new AbortController().signal);
});

for (const [name, data] of [
  ['unrelated service on the API port', { status: 'UP', database: 'UP', application: 'different-service' }],
  ['database down', { status: 'UP', database: 'DOWN', application: 'aluminum-manager' }],
  ['missing identity', { status: 'UP' }],
]) {
  test(`rejects ${name}`, async () => {
    await assert.rejects(loadReadyGate({ success: true, data })(new AbortController().signal));
  });
}

test('rejects connection failure', async () => {
  await assert.rejects(loadReadyGate(null, true)(new AbortController().signal));
});
