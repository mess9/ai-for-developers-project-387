import {useCallback, useEffect, useState} from 'react'
import {useConfig} from '../hooks/useConfig'
import {apiClient} from '../api/client'
import type {components} from '../api/types'
import {OwnerBanner} from '../components/OwnerBanner'
import {EventTypeList} from '../components/EventTypeList'
import {AvailabilityCalendar} from '../components/AvailabilityCalendar'
import {SlotList} from '../components/SlotList'
import {BookingForm, type BookingFormData} from '../components/BookingForm'
import {Toast} from '../components/Toast'
import {BookingError, fieldErrors, problemMessage} from '../lib/errors'
import {parsePlainDate} from '../lib/time'
import {format} from 'date-fns'
import {ru} from 'date-fns/locale'

type EventType = components['schemas']['EventType']
type Availability = components['schemas']['Availability']
type AvailableSlot = components['schemas']['AvailableSlot']

const SLOT_STALE_CODES = ['SLOT_IN_PAST', 'SLOT_OUT_OF_HORIZON', 'SLOT_NOT_ON_GRID']

export function VisitorPage() {
  const { config } = useConfig()
  const [eventTypes, setEventTypes] = useState<EventType[] | null>(null)
  const [selectedType, setSelectedType] = useState<EventType | null>(null)
  const [availability, setAvailability] = useState<Availability | null>(null)
  const [loadingAvail, setLoadingAvail] = useState(false)
  const [selectedDate, setSelectedDate] = useState<string | null>(null)
  const [bookingSlot, setBookingSlot] = useState<AvailableSlot | null>(null)
  const [toast, setToast] = useState<string | null>(null)

  useEffect(() => {
    apiClient.GET('/event-types').then(({ data }) => setEventTypes(data ?? []))
  }, [])

  const fetchAvailability = useCallback(async (eventTypeId: string) => {
    setLoadingAvail(true)
    const { data } = await apiClient.GET('/event-types/{id}/availability', {
      params: { path: { id: eventTypeId } },
    })
    setAvailability(data ?? null)
    setLoadingAvail(false)
  }, [])

  const handleSelectType = (et: EventType) => {
    setSelectedType(et)
    setSelectedDate(null)
    setAvailability(null)
    fetchAvailability(et.id)
  }

  const backToTypes = () => {
    setSelectedType(null)
    setAvailability(null)
    setSelectedDate(null)
    setBookingSlot(null)
  }

  const handleBook = async (formData: BookingFormData) => {
    if (!selectedType || !bookingSlot) return

    const { error } = await apiClient.POST('/bookings', {
      body: {
        eventTypeId: selectedType.id,
        startAt: bookingSlot.startAt,
        ...formData,
      },
    })

    if (error) {
      const code = error.errorCode
      if (code === 'VALIDATION_FAILED') {
        throw new BookingError(problemMessage(error), fieldErrors(error))
      }
      if (code === 'SLOT_ALREADY_BOOKED') {
        setBookingSlot(null)
        await fetchAvailability(selectedType.id)
        setToast('Это время только что заняли')
        return
      }
      if (code === 'EVENT_TYPE_NOT_FOUND') {
        backToTypes()
        setEventTypes(null)
        apiClient.GET('/event-types').then(({ data }) => setEventTypes(data ?? []))
        setToast('Тип события не найден')
        return
      }
      if (SLOT_STALE_CODES.includes(code)) {
        setBookingSlot(null)
        await fetchAvailability(selectedType.id)
        setToast('Доступность устарела — список обновлён')
        return
      }
      throw new BookingError(problemMessage(error))
    }

    // 201 — успех
    setBookingSlot(null)
    setSelectedDate(null)
    await fetchAvailability(selectedType.id)
    setToast('Вы записаны')
  }

  if (!config) {
    return <div className="p-4">Загрузка…</div>
  }

  const selectedDay = availability?.days.find((d) => d.date === selectedDate)

  return (
    <div className="min-h-screen bg-gray-50">
      <div className="max-w-4xl mx-auto p-4">
        <header className="mb-4">
          <h1 className="text-2xl font-bold mb-3">Запись на звонок</h1>
          <OwnerBanner config={config} />
        </header>

        {!selectedType ? (
          eventTypes === null ? (
            <div className="p-4">Загрузка типов встреч…</div>
          ) : (
            <EventTypeList eventTypes={eventTypes} onSelect={handleSelectType} />
          )
        ) : (
          <>
            <div className="flex items-center gap-3 mb-4">
              <button
                type="button"
                onClick={backToTypes}
                className="text-blue-600 hover:underline"
              >
                ← К типам встреч
              </button>
              <span className="font-semibold">
                {selectedType.name} · {selectedType.durationMinutes} мин
              </span>
            </div>

            {loadingAvail || !availability ? (
              <div className="p-4">Загрузка доступности…</div>
            ) : (
              <>
                <AvailabilityCalendar
                  days={availability.days}
                  selectedDate={selectedDate}
                  onSelectDate={setSelectedDate}
                />

                {selectedDate && selectedDay && (
                  <div className="bg-white rounded-lg shadow p-4">
                    <h2 className="text-lg font-bold mb-1">
                      Слоты на{' '}
                      {format(parsePlainDate(selectedDate), 'd MMMM yyyy', { locale: ru })}
                    </h2>
                    <p className="text-sm text-gray-500 mb-3">
                      Время — в поясе владельца ({availability.ownerTimeZone})
                    </p>
                    <SlotList
                      slots={selectedDay.slots}
                      ownerTimeZone={availability.ownerTimeZone}
                      onSelect={setBookingSlot}
                    />
                  </div>
                )}
              </>
            )}
          </>
        )}

        {bookingSlot && selectedType && (
          <BookingForm
            eventType={selectedType}
            slot={bookingSlot}
            ownerTimeZone={availability?.ownerTimeZone ?? config.ownerTimeZone}
            onSubmit={handleBook}
            onClose={() => setBookingSlot(null)}
          />
        )}

        {toast && <Toast message={toast} onDismiss={() => setToast(null)} />}
      </div>
    </div>
  )
}
