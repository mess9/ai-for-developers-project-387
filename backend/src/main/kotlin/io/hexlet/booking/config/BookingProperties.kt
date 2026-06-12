package io.hexlet.booking.config

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.LocalTime
import java.time.ZoneId

@ConfigurationProperties(prefix = "booking")
data class BookingProperties(
    val ownerTz: String = "Asia/Yerevan",
    /** Формат: "HH:00-HH:00", только целые часы, например "09:00-18:00". */
    val workingHours: String = "09:00-18:00",
    /** Шаг сетки стартов в минутах. Фиксирован — 15. */
    val gridMinutes: Int = 15,
    /** На сколько дней вперёд (от сегодня) доступно бронирование. */
    val horizonDays: Int = 14,
) {
    val zone: ZoneId get() = ZoneId.of(ownerTz)
    val workStart: LocalTime get() = LocalTime.parse(workingHours.substringBefore("-"))
    val workEnd: LocalTime get() = LocalTime.parse(workingHours.substringAfter("-"))
}
