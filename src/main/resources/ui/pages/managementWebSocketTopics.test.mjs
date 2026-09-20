import assert from 'node:assert/strict';
import { createRequire } from 'node:module';
import { test } from 'node:test';
import path from 'node:path';
import { build } from 'esbuild';
import React from 'react';
import { renderToString } from 'react-dom/server';

const require = createRequire(import.meta.url);

async function subscribedTopics(page) {
  globalThis.__managementPageTopics = [];
  const entry = path.resolve(`pages/${page}.tsx`);
  const result = await build({
    entryPoints: [entry],
    bundle: true,
    format: 'cjs',
    platform: 'node',
    write: false,
    external: ['react', 'react-dom/server'],
    plugins: [{
      name: 'management-page-test-doubles',
      setup(build) {
        build.onResolve({ filter: /hooks[\\/]useWebsocket$/ }, () => ({ path: 'websocket', namespace: 'test-double' }));
        build.onResolve({ filter: /hooks[\\/]useRefreshOnTabActivate$/ }, () => ({ path: 'refresh', namespace: 'test-double' }));
        build.onResolve({ filter: /services[\\/](unit|brand)apiservice$/ }, () => ({ path: 'api', namespace: 'test-double' }));
        build.onResolve({ filter: /^sweetalert2$/ }, () => ({ path: 'swal', namespace: 'test-double' }));
        build.onLoad({ filter: /.*/, namespace: 'test-double' }, ({ path: mock }) => {
          if (mock === 'websocket') {
            return { contents: 'export const useWebsocket = topic => globalThis.__managementPageTopics.push(topic)' };
          }
          if (mock === 'refresh') return { contents: 'export const useRefreshOnTabActivate = () => {}' };
          if (mock === 'api') {
            return { contents: 'const service = { getAll: async () => [], create: async () => {}, update: async () => {}, delete: async () => {} }; export { service as unitService, service as brandService }' };
          }
          return { contents: 'export default { fire: async () => ({ isConfirmed: false }) }' };
        });
      },
    }],
  });

  const module = { exports: {} };
  new Function('require', 'module', 'exports', result.outputFiles[0].text)(require, module, module.exports);
  renderToString(React.createElement(module.exports.default));
  return globalThis.__managementPageTopics;
}

test('unit and brand management pages subscribe to isolated topics', async () => {
  assert.deepEqual(await subscribedTopics('UnitManagement'), ['/topic/unit']);
  assert.deepEqual(await subscribedTopics('BrandManagement'), ['/topic/brand']);
});
