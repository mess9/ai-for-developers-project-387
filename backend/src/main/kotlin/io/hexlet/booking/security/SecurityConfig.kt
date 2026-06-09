package io.hexlet.booking.security

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter

@Configuration
@EnableWebSecurity
class SecurityConfig(private val bearerFilter: BearerTokenFilter) {

    @Bean
    fun filterChain(http: HttpSecurity): SecurityFilterChain {
        http
            .csrf { it.disable() }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .addFilterBefore(bearerFilter, UsernamePasswordAuthenticationFilter::class.java)
            .authorizeHttpRequests { auth ->
                // admin/** защищено — context-path /api/v1 стрипается Spring Security
                auth.requestMatchers("/admin/**").authenticated()
                auth.anyRequest().permitAll()
            }
            .exceptionHandling { ex ->
                ex.authenticationEntryPoint { _, res, _ ->
                    res.status = HttpStatus.UNAUTHORIZED.value()
                    res.contentType = MediaType.APPLICATION_PROBLEM_JSON_VALUE
                    res.writer.write(
                        """{"status":401,"errorCode":"UNAUTHORIZED","detail":"Missing or invalid token"}"""
                    )
                }
            }
        return http.build()
    }
}
