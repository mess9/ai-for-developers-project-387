import type {components} from '../api/types'

type Config = components['schemas']['Config']

interface OwnerBannerProps {
  config: Config
}

/** Глобальный баннер владельца: часовой пояс и рабочие часы. */
export function OwnerBanner({ config }: OwnerBannerProps) {
  return (
    <div className="bg-blue-50 border border-blue-200 rounded-lg px-4 py-3 mb-6 text-sm text-blue-900">
      Часовой пояс владельца: <strong>{config.ownerTimeZone}</strong>. Рабочие часы:{' '}
      <strong>
        {config.workingHours.start}–{config.workingHours.end}
      </strong>
      .
    </div>
  )
}
