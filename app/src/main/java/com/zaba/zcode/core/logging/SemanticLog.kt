package com.zaba.zcode.core.logging

/**
 * Makna log tidak boleh ditebak dari emoji/teks. UI bebas menentukan warna dan
 * label; producer hanya mengirim kind + pesan manusia.
 */
enum class SemanticLogKind {
    STEP,
    INFO,
    WARN,
    WAIT,
    OK,
    FAIL,
    STOP,
    RAW,
}

data class SemanticLog(
    val text: String,
    val kind: SemanticLogKind,
)
