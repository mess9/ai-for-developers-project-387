package io.hexlet.booking.security

import io.hexlet.booking.service.TokenService
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpHeaders
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

@Component
class BearerTokenFilter(private val tokenService: TokenService) : OncePerRequestFilter() {

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        chain: FilterChain,
    ) {
        val header = request.getHeader(HttpHeaders.AUTHORIZATION)
        if (header != null && header.startsWith("Bearer ")) {
            val token = header.removePrefix("Bearer ").trim()
            if (tokenService.isValid(token)) {
                val auth = UsernamePasswordAuthenticationToken(
                    "owner", null, listOf(SimpleGrantedAuthority("ROLE_ADMIN"))
                )
                SecurityContextHolder.getContext().authentication = auth
            }
        }
        chain.doFilter(request, response)
    }
}
