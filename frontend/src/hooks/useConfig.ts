import {useEffect, useState} from 'react'
import {apiClient} from '../api/client'
import type {components} from '../api/types'

type Config = components['schemas']['Config']

export function useConfig() {
  const [config, setConfig] = useState<Config | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    const fetchConfig = async () => {
      try {
        const { data, error } = await apiClient.GET('/config')
        if (error) {
          setError('Failed to load config')
        } else {
          setConfig(data)
        }
      } catch {
        setError('Failed to load config')
      } finally {
        setLoading(false)
      }
    }

    fetchConfig()
  }, [])

  return { config, loading, error }
}
