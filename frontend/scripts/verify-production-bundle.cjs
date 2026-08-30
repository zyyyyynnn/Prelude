#!/usr/bin/env node
'use strict'

const fs = require('node:fs')
const path = require('node:path')

const dist = path.resolve(__dirname, '..', 'dist')
if (!fs.existsSync(path.join(dist, 'index.html'))) {
  throw new Error('Production bundle is missing')
}
const files = fs.readdirSync(path.join(dist, 'assets')).filter((name) => /\.(?:js|css)$/.test(name))
const source = files
  .map((name) => fs.readFileSync(path.join(dist, 'assets', name), 'utf8'))
  .join('\n')
if (source.includes('/components-lab') || source.includes('Component Lab')) {
  throw new Error('Development route found in production bundle')
}
console.log('Production bundle verification: PASS')
