import {describe, expect, it, vi} from 'vitest'
import {render, screen} from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import {AvailabilityCalendar} from '../components/AvailabilityCalendar'
import type {components} from '../api/types'

type AvailabilityDay = components['schemas']['AvailabilityDay']

const day = (date: string, hasFreeSlots: boolean): AvailabilityDay => ({
  date,
  hasFreeSlots,
  slots: [],
})

describe('AvailabilityCalendar', () => {
  it('рисует одну месячную сетку, если окно внутри месяца', () => {
    const days = [day('2026-06-10', false), day('2026-06-11', true), day('2026-06-12', false)]
    render(<AvailabilityCalendar days={days} selectedDate={null} onSelectDate={() => {}} />)

    expect(screen.getAllByRole('heading', { level: 3 })).toHaveLength(1)
  })

  it('рисует две месячные сетки, если окно пересекает границу месяцев', () => {
    const days = [day('2026-01-28', true), day('2026-02-03', true)]
    render(<AvailabilityCalendar days={days} selectedDate={null} onSelectDate={() => {}} />)

    expect(screen.getAllByRole('heading', { level: 3 })).toHaveLength(2)
  })

  it('дни без свободных слотов неактивны, со свободными — кликабельны', async () => {
    const onSelect = vi.fn()
    const days = [day('2026-06-10', false), day('2026-06-11', true)]
    render(<AvailabilityCalendar days={days} selectedDate={null} onSelectDate={onSelect} />)

    expect(screen.getByRole('button', { name: '10' })).toBeDisabled()
    const freeDay = screen.getByRole('button', { name: '11' })
    expect(freeDay).toBeEnabled()

    await userEvent.click(freeDay)
    expect(onSelect).toHaveBeenCalledWith('2026-06-11')
  })
})
