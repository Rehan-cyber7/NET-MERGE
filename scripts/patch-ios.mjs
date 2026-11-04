// After `npx cap add ios`: make the Documents folder show up in the Files app, so downloads can be found.
import { readFileSync, writeFileSync, existsSync } from 'node:fs'

const file = 'ios/App/App/Info.plist'
if (!existsSync(file)) {
  console.error(`${file} not found: run "npx cap add ios" first`)
  process.exit(1)
}
let plist = readFileSync(file, 'utf8')
if (plist.includes('UIFileSharingEnabled')) {
  console.log('Info.plist already patched')
} else {
  const keys = '\t<key>UIFileSharingEnabled</key>\n\t<true/>\n\t<key>LSSupportsOpeningDocumentsInPlace</key>\n\t<true/>\n'
  plist = plist.replace(/<\/dict>\s*<\/plist>\s*$/, `${keys}</dict>\n</plist>\n`)
  writeFileSync(file, plist)
  console.log('Info.plist patched')
}
