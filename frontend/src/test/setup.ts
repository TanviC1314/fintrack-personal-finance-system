import { cleanup } from '@testing-library/react'
import '@testing-library/jest-dom/vitest'
import { afterEach } from 'vitest'

// Testing Library only unmounts between tests by itself when test globals are enabled.
afterEach(cleanup)
