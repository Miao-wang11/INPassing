/** Validate uni-app SFC syntax with Vue's real SFC compiler from admin dependencies. */
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { createRequire } from 'node:module'
import { fileURLToPath } from 'node:url'
import path from 'node:path'

const require = createRequire(new URL('../../admin/package.json', import.meta.url))
const { parse, compileScript, compileTemplate } = require('@vue/compiler-sfc')
const root = fileURLToPath(new URL('../', import.meta.url))
const views = ['pages-agent/assistant/assistant.vue', 'pages/index/index.vue']
for (const file of views) {
  const filename = path.join(root, file)
  const { descriptor, errors } = parse(readFileSync(filename, 'utf8'), { filename })
  assert.deepEqual(errors, [], file + ': invalid SFC')
  assert.ok(descriptor.template && descriptor.scriptSetup, file + ': missing template/script')
  const script = compileScript(descriptor, { id: 'agent-stage5' })
  assert.ok(script.content.includes('setup'), file + ': script compilation')
  const template = compileTemplate({
    source: descriptor.template.content, filename,
    id: 'agent-stage5', compilerOptions: { mode: 'module' },
  })
  assert.deepEqual(template.errors, [], file + ': invalid Vue template')
}
const pageMap = JSON.parse(readFileSync(path.join(root, 'pages.json'), 'utf8'))
assert.ok(pageMap.subPackages.some(x => x.root === 'pages-agent'
  && x.pages.some(p => p.path === 'assistant/assistant')))
assert.equal(pageMap.tabBar.list.length, 5, 'original tab bar must remain intact')
console.log('Vue SFC compiler: assistant, home and page routing passed')
