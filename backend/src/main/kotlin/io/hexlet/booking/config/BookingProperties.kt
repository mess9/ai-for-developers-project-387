package io.hexlet.booking.config

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.LocalTime
import java.time.ZoneId

@ConfigurationProperties(prefix = "booking")
data class BookingProperties(
    val ownerTz: String = "Asia/Yerevan",
    /** Формат: "HH:mm-HH:mm", например "09:00-18:00" */
    val workingHours: String = "09:00-18:00",
    val slotMinutes: Int = 30,
    val horizonDays: Int = 31,
) {
    val zone: ZoneId get() = ZoneId.of(ownerTz)
    val workStart: LocalTime get() = LocalTime.parse(workingHours.substringBefore("-"))
    val workEnd: LocalTime get() = LocalTime.parse(workingHours.substringAfter("-"))
}
