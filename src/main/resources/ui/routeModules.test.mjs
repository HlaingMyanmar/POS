import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import { test } from 'node:test';

const appSource = await readFile(new URL('./App.tsx', import.meta.url), 'utf8');
const routeModulesSource = await readFile(new URL('./routeModules.ts', import.meta.url), 'utf8');

test('major pages are route-level dynamic imports', () => {
  const majorPages = [
    'Dashboard',
    'ProductManagement',
    'PurchaseManagement',
    'SaleManagement',
    'CustomerAppOrdersPage',
    'ServiceJobManagement',
    'OutdoorTracking'
  ];

  for (const page of majorPages) {
    assert.match(
      routeModulesSource,
      new RegExp(`page\\(\\(\\) => import\\(['"]\\./pages/${page}['"]\\)\\)`),
      `${page} should be loaded with import()`
    );
  }
});

test('App does not eagerly import page modules', () => {
  assert.doesNotMatch(
    appSource,
    /from\s+['"]\.\/pages\//,
    'route pages must be imported through the lazy route registry'
  );
  assert.match(appSource, /from ['"]\.\/routeModules['"]/);
  assert.match(appSource, /<RouteLoadBoundary>/);
});

test('lazy route registry has no static page imports', () => {
  assert.doesNotMatch(
    routeModulesSource,
    /import\s+(?!type\b)[^;]*\sfrom\s+['"]\.\/pages\//,
    'a static page import would pull the module into the entry chunk'
  );
  assert.match(routeModulesSource, /export const preloadRoute/);
});
