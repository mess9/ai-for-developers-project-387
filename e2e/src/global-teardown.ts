import { shouldManageStack, stopStack } from './stack.js'

/** Гасит e2e-стек, если прогон сам его поднимал. */
async function globalTeardown(): Promise<void> {
  if (shouldManageStack()) {
    stopStack() // docker compose ... down -v
  }
}

export default globalTeardown
