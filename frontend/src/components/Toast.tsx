import {useEffect} from 'react'

interface ToastProps {
  message: string
  onDismiss: () => void
}

/** Короткое всплывающее уведомление, само исчезает через 4 секунды. */
export function Toast({ message, onDismiss }: ToastProps) {
  useEffect(() => {
    const id = setTimeout(onDismiss, 4000)
    return () => clearTimeout(id)
  }, [message, onDismiss])

  return (
    <div
      role="status"
      className="fixed bottom-4 left-1/2 -translate-x-1/2 bg-gray-900 text-white px-4 py-2 rounded shadow-lg z-50"
    >
      {message}
    </div>
  )
}
