package io.hexlet.booking.security

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.core.io.ClassPathResource
import org.springframework.core.io.Resource
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer
import org.springframework.web.servlet.resource.PathResourceResolver

@Configuration
@EnableWebSecurity
@Order(Ordered.HIGHEST_PRECEDENCE)
class SecurityConfig(private val bearerFilter: BearerTokenFilter) : WebMvcConfigurer {

    @Bean
    fun filterChain(http: HttpSecurity): SecurityFilterChain {
        http
            .csrf { it.disable() }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .addFilterBefore(bearerFilter, UsernamePasswordAuthenticationFilter::class.java)
            .authorizeHttpRequests { auth ->
                auth.requestMatchers("/api/v1/admin/**").authenticated()
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

    override fun addResourceHandlers(registry: ResourceHandlerRegistry) {
        registry
            .addResourceHandler("/**")
            .addResourceLocations("classpath:/static/")
            .resourceChain(false)
            .addResolver(SpaPathResourceResolver())
    }

    override fun addViewControllers(registry: ViewControllerRegistry) {
        registry.addViewController("/").setViewName("forward:/index.html")
    }

    /**
     * Отдаёт реальный статический файл, если он существует; иначе — для любого
     * не-API / не-actuator пути возвращает index.html, чтобы deep-link и F5 на
     * клиентских маршрутах SPA (например, /admin) работали. Forward, а не redirect,
     * поэтому URL в адресной строке сохраняется.
     */
    private class SpaPathResourceResolver : PathResourceResolver() {
        override fun getResource(resourcePath: String, location: Resource): Resource? {
            val requested = location.createRelative(resourcePath)
            if (requested.exists() && requested.isReadable) {
                return requested
            }
            if (resourcePath.startsWith("api/") || resourcePath.startsWith("actuator/")) {
                return null
            }
            return ClassPathResource("/static/index.html").takeIf { it.exists() }
        }
    }
}
