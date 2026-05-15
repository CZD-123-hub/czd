import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { useGraphStore } from './graph'
import * as graphApi from '@/api/graph'
import type { KnowledgeNode } from '@/types'

vi.mock('@/api/graph', () => ({
  getOverview: vi.fn(),
  getNodeDetail: vi.fn(),
  getNeighbors: vi.fn(),
  searchNodes: vi.fn(),
}))

function node(id: string, name: string): KnowledgeNode {
  return {
    id,
    name,
    category: 'framework',
    description: `${name} description`,
    difficulty: 'medium',
    keywords: [name.toLowerCase()],
  }
}

describe('graph store', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })

  it('maps 404 detail error to not-found recoverable message', async () => {
    const store = useGraphStore()

    vi.mocked(graphApi.getNodeDetail).mockRejectedValue({
      response: {
        status: 404,
        data: { message: 'node not found' },
      },
    })

    const ok = await store.selectNode('missing')

    expect(ok).toBe(false)
    expect(store.detailErrorCode).toBe(404)
    expect(store.detailError).toContain('不存在')
  })

  it('maps 500 detail error to service-unavailable recoverable message', async () => {
    const store = useGraphStore()

    vi.mocked(graphApi.getNodeDetail).mockRejectedValue({
      response: {
        status: 500,
        data: { message: 'neo4j down' },
      },
    })

    const ok = await store.selectNode('java')

    expect(ok).toBe(false)
    expect(store.detailErrorCode).toBe(500)
    expect(store.detailError).toContain('暂时不可用')
  })

  it('tracks expand depth and highlights newly added nodes on incremental expand', async () => {
    const store = useGraphStore()
    store.nodes = [node('java', 'Java')]
    store.edges = []

    vi.mocked(graphApi.getNeighbors).mockResolvedValue({
      data: {
        code: 200,
        message: 'ok',
        data: {
          nodes: [node('java', 'Java'), node('spring-boot', 'Spring Boot')],
          edges: [{ source: 'java', target: 'spring-boot', type: 'DEPENDS_ON' }],
        },
      },
    } as any)

    const result = await store.loadNeighbors('java', { incremental: true })

    expect(result.failed).toBe(false)
    expect(result.depth).toBe(1)
    expect(result.addedNodes).toBe(1)
    expect(store.getExpandedDepth('java')).toBe(1)
    expect(store.recentlyAddedNodeIds).toEqual(['spring-boot'])
  })
})
