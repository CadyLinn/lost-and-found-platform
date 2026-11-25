import type { QueryClient } from '@tanstack/react-query'

/** Refresh everything that could show this post after an admin changes it. */
export function refreshAfterModeration(queryClient: QueryClient, itemId?: number) {
  const keys: unknown[][] = [['admin'], ['items'], ['my-items'], ['matches']]
  if (itemId) keys.push(['item', itemId])
  for (const key of keys) queryClient.invalidateQueries({ queryKey: key })
}
