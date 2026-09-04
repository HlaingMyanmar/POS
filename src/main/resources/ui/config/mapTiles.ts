/** OSM-based Carto raster tiles — shared with Android OSMDroid (Carto Voyager). */
const CARTO_KEY = 'cb1_2u3y_1_b3d4615afe0884a3e637745e';

export const CARTO_VOYAGER_TILE =
  `https://{s}.basemaps.cartocdn.com/rastertiles/voyager/{z}/{x}/{y}.png?key=${CARTO_KEY}`;

export const CARTO_POSITRON_TILE =
  `https://{s}.basemaps.cartocdn.com/rastertiles/light_all/{z}/{x}/{y}.png?key=${CARTO_KEY}`;

export const CARTO_ATTR =
  '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors &copy; <a href="https://carto.com/attributions">CARTO</a>';

export function addCartoBaseLayers(
  L: any,
  map: any,
  onMessage?: (message: string) => void
) {
  const voyager = L.tileLayer(CARTO_VOYAGER_TILE, {
    attribution: CARTO_ATTR,
    maxZoom: 19,
    subdomains: 'abc'
  });
  const positron = L.tileLayer(CARTO_POSITRON_TILE, {
    attribution: CARTO_ATTR,
    maxZoom: 19,
    subdomains: 'abc'
  });

  let failedTiles = 0;
  voyager.on('tileload', () => {
    failedTiles = 0;
    onMessage?.('');
  });
  voyager.on('tileerror', () => {
    failedTiles += 1;
    if (failedTiles < 3 || !map.hasLayer(voyager)) return;
    map.removeLayer(voyager);
    positron.addTo(map);
    onMessage?.('Voyager မရသဖြင့် Carto Positron သို့ အလိုအလျောက်ပြောင်းထားသည်။');
  });
  positron.on('tileerror', () => {
    onMessage?.('Map tile internet မရပါ။ Network/Firewall ကို စစ်ပါ။ Live list ဆက်သုံးနိုင်သည်။');
  });

  voyager.addTo(map);
  L.control.layers(
    { 'Carto Voyager': voyager, 'Carto Positron': positron },
    undefined,
    { position: 'topright' }
  ).addTo(map);

  return { voyager, positron };
}

export function addCartoTileLayer(L: any, map: any, onMessage?: (message: string) => void) {
  const voyager = L.tileLayer(CARTO_VOYAGER_TILE, {
    attribution: CARTO_ATTR,
    maxZoom: 19,
    subdomains: 'abc'
  });
  const positron = L.tileLayer(CARTO_POSITRON_TILE, {
    attribution: CARTO_ATTR,
    maxZoom: 19,
    subdomains: 'abc'
  });

  let failedTiles = 0;
  voyager.on('tileload', () => {
    failedTiles = 0;
  });
  voyager.on('tileerror', () => {
    failedTiles += 1;
    if (failedTiles < 3 || !map.hasLayer(voyager)) return;
    map.removeLayer(voyager);
    positron.addTo(map);
    onMessage?.('Voyager မရသဖြင့် Carto Positron သို့ ပြောင်းထားသည်။');
  });
  positron.on('tileerror', () => {
    onMessage?.('Map tile internet မရပါ။');
  });

  voyager.addTo(map);
  return voyager;
}
