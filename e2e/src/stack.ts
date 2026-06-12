import { execFileSync } from 'node:child_process'
import path from 'node:path'

// e2e/src → repo root (две папки вверх).
const repoRoot = path.resolve(import.meta.dirname, '..', '..')
const composeFile = path.join(repoRoot, 'compose.e2e.yaml')

/**
 * Управлять ли жизненным циклом e2e-стека из прогона.
 * - Явный флаг `E2E_MANAGE_STACK=1|0` имеет приоритет.
 * - Иначе: в CI — да (стенд эфемерный), локально — нет (стенд поднимаете сами).
 */
export function shouldManageStack(): boolean {
  const flag = process.env.E2E_MANAGE_STACK
  if (flag === '1' || flag === 'true') return true
  if (flag === '0' || flag === 'false') return false
  return !!process.env.CI
}

function compose(args: string[]): void {
  execFileSync('docker', ['compose', '-f', composeFile, ...args], {
    cwd: repoRoot,
    stdio: 'inherit',
  })
}

export function startStack(): void {
  compose(['up', '-d', '--build', '--wait'])
}

export function stopStack(): void {
  compose(['down', '-v'])
}
