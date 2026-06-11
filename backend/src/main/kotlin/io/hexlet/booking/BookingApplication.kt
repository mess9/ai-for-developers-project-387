package io.hexlet.booking

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.runApplication
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.FilterType
import org.springframework.scheduling.annotation.EnableScheduling

@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling
@ComponentScan(
    basePackages = ["io.hexlet.booking"],
    excludeFilters = [
        ComponentScan.Filter(
            pattern = ["io.hexlet.booking.api.*Api"],
            type = FilterType.REGEX
        )
    ]
)
class BookingApplication

fun main(args: Array<String>) {
    runApplication<BookingApplication>(*args)
}
