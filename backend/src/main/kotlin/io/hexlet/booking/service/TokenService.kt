package io.hexlet.booking.service

import io.hexlet.booking.db.Tables.APP_SETTINGS
import jakarta.annotation.PostConstruct
import org.jooq.DSLContext
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.security.SecureRandom
import java.util.*

@Service
class TokenService(private val dsl: DSLContext) {

    private val log = LoggerFactory.getLogger(TokenService::class.java)
    private lateinit var token: String

    @PostConstruct
    fun init() {
        token = loadOrCreate()
        log.info("=== Owner token: {} ===", token)
    }

    fun isValid(candidate: String): Boolean = candidate == token

    /** Доступ из тестов */
    fun currentToken(): String = token

    private fun loadOrCreate(): String {
        val existing = dsl.selectFrom(APP_SETTINGS)
            .where(APP_SETTINGS.ID.eq(ROW_ID))
            .fetchOne()
        if (existing != null) return existing.token

        val newToken = generateToken()
        dsl.insertInto(APP_SETTINGS)
            .set(APP_SETTINGS.ID, ROW_ID)
            .set(APP_SETTINGS.TOKEN, newToken)
            .execute()
        return newToken
    }

    private fun generateToken(): String {
        val bytes = ByteArray(32)
        SecureRandom().nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    companion object {
        private val ROW_ID: Short = 1
    }
}
