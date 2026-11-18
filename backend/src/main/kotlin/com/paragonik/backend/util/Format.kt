package com.paragonik.backend.util

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.absoluteValue

private const val NBSP = '\u00A0'
private val polishDate = DateTimeFormatter.ofPattern("dd.MM.yyyy")

fun formatPln(grosze: Long): String {
    val sign = if (grosze < 0) "-" else ""
    val abs = grosze.absoluteValue
    val zlote = (abs / 100).toString().reversed().chunked(3).joinToString(NBSP.toString()).reversed()
    val gr = (abs % 100).toString().padStart(2, '0')
    return "$sign$zlote,$gr${NBSP}zł"
}

fun formatDate(date: LocalDate): String = date.format(polishDate)
