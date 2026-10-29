// Kopieert bij elke build de bestanden die de web-app deelt met de Android-app, zodat er één bron
// van waarheid blijft:
//   - hand_landmarker.task: hetzelfde MediaPipe-model als de Android-app (shared/src/androidMain/assets)
//   - templates.json: dezelfde gebundelde herkenningstemplates (composeApp/.../files/templates.json)
//   - de MediaPipe-wasm-bestanden uit node_modules, zodat alles van ons eigen domein komt
//     (geen verzoeken naar een CDN van Google vanuit de klas).
import { copyFileSync, existsSync, mkdirSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const root = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const repo = resolve(root, '..');

const copies = [
  [resolve(repo, 'shared/src/androidMain/assets/hand_landmarker.task'), 'public/mediapipe/hand_landmarker.task'],
  [resolve(repo, 'composeApp/src/commonMain/composeResources/files/templates.json'), 'public/templates.json'],
  ...['vision_wasm_internal.js', 'vision_wasm_internal.wasm', 'vision_wasm_nosimd_internal.js', 'vision_wasm_nosimd_internal.wasm']
    .map((f) => [resolve(root, 'node_modules/@mediapipe/tasks-vision/wasm', f), `public/mediapipe/wasm/${f}`]),
];

for (const [from, to] of copies) {
  if (!existsSync(from)) {
    console.error(`Ontbreekt: ${from}`);
    process.exit(1);
  }
  const target = resolve(root, to);
  mkdirSync(dirname(target), { recursive: true });
  copyFileSync(from, target);
}
console.log(`prepare-assets: ${copies.length} bestanden klaargezet`);
