package com.squarenova.emaanwallpapers.data

import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.time.temporal.ChronoField

/**
 * Parses timestamp strings returned by Supabase PostgREST / Postgres.
 *
 * Supports:
 * - ISO-8601: `2026-06-27T13:12:35.476Z`, `2026-06-27T13:12:35.476+00:00`
 * - Postgres default: `2026-06-27 13:12:35.476` (space separator, assumed UTC)
 */
object SupabaseTimestampParser {

    private val postgresSpaceFormatter: DateTimeFormatter = DateTimeFormatterBuilder()
        .appendPattern("yyyy-MM-dd HH:mm:ss")
        .optionalStart()
        .appendFraction(ChronoField.NANO_OF_SECOND, 1, 9, true)
        .optionalEnd()
        .toFormatter()

    private val postgresTFormatter: DateTimeFormatter = DateTimeFormatterBuilder()
        .appendPattern("yyyy-MM-dd'T'HH:mm:ss")
        .optionalStart()
        .appendFraction(ChronoField.NANO_OF_SECOND, 1, 9, true)
        .optionalEnd()
        .toFormatter()

    /** @return epoch millis in UTC, or null if the value cannot be parsed */
    fun parseToEpochMillis(value: String?): Long? {
        if (value.isNullOrBlank()) return null
        val raw = value.trim()

        parseIsoInstant(raw)?.let { return it }
        parseOffsetDateTime(raw)?.let { return it }
        parsePostgresLocal(raw)?.let { return it }

        val normalized = normalizeSpaceToIsoUtc(raw)
        if (normalized != raw) {
            parseIsoInstant(normalized)?.let { return it }
        }

        return null
    }

    fun isInFuture(value: String?): Boolean {
        val endMs = parseToEpochMillis(value) ?: return false
        return System.currentTimeMillis() < endMs
    }

    private fun parseIsoInstant(raw: String): Long? = runCatching {
        Instant.parse(raw).toEpochMilli()
    }.getOrNull()

    private fun parseOffsetDateTime(raw: String): Long? = runCatching {
        OffsetDateTime.parse(raw).toInstant().toEpochMilli()
    }.getOrNull()

    private fun parsePostgresLocal(raw: String): Long? {
        val formatter = when {
            'T' in raw -> postgresTFormatter
            ' ' in raw -> postgresSpaceFormatter
            else -> return null
        }
        return runCatching {
            LocalDateTime.parse(raw, formatter)
                .atZone(ZoneOffset.UTC)
                .toInstant()
                .toEpochMilli()
        }.getOrNull()
    }

    /** `2026-06-27 13:12:35.476` → `2026-06-27T13:12:35.476Z` when no zone is present */
    private fun normalizeSpaceToIsoUtc(raw: String): String {
        if ('T' in raw) return raw
        if (!raw.contains(' ')) return raw
        val withT = raw.replaceFirst(' ', 'T')
        val hasZone = withT.endsWith('Z') ||
            Regex("[+-]\\d{2}:\\d{2}$").containsMatchIn(withT)
        return if (hasZone) withT else "${withT}Z"
    }
}
