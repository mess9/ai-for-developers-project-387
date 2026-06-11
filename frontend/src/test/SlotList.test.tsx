import {describe, expect, it, vi} from 'vitest'
import {render, screen} from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import {SlotList} from '../components/SlotList'

const slots = [
  { startAt: '2026-06-11T09:00:00+03:00', endAt: '2026-06-11T09:30:00+03:00' },
  { startAt: '2026-06-11T09:30:00+03:00', endAt: '2026-06-11T10:00:00+03:00' },
]

describe('SlotList', () => {
  it('показывает только свободные слоты во времени владельца', () => {
    render(<SlotList slots={slots} ownerTimeZone="Europe/Moscow" onSelect={() => {}} />)
    expect(screen.getByText('09:00')).toBeInTheDocument()
    expect(screen.getByText('09:30')).toBeInTheDocument()
  })

  it('сообщает, если слотов нет', () => {
    render(<SlotList slots={[]} ownerTimeZone="Europe/Moscow" onSelect={() => {}} />)
    expect(screen.getByText(/нет свободных слотов/i)).toBeInTheDocument()
  })

  it('вызывает onSelect с выбранным слотом', async () => {
    const onSelect = vi.fn()
    render(<SlotList slots={slots} ownerTimeZone="Europe/Moscow" onSelect={onSelect} />)
    await userEvent.click(screen.getByText('09:00'))
    expect(onSelect).toHaveBeenCalledWith(slots[0])
  })
})
