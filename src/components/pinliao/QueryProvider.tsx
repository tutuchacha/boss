/**
 * 聘聊 · TanStack Query Provider
 * =====================================================================
 * 当前 Mock 模式下不依赖 Query 也能跑；但保留 Provider，便于 Step 3 联调阶段
 * 直接用 useQuery/useMutation，组件无需改动。
 * =====================================================================
 */
'use client'

import { useState } from 'react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'

export function QueryProvider({ children }: { children: React.ReactNode }) {
  const [client] = useState(
    () =>
      new QueryClient({
        defaultOptions: {
          queries: {
            staleTime: 30_000,
            refetchOnWindowFocus: false,
            retry: 1,
          },
        },
      }),
  )
  return <QueryClientProvider client={client}>{children}</QueryClientProvider>
}
