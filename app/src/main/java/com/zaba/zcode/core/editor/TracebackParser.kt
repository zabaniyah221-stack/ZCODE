package com.zaba.zcode.core.editor

import java.io.File

/**
 * TracebackParser — A3+A6 Gerbong A v1.0.19.
 *
 * A3: kenali baris traceback Python `File "X", line N` supaya terminal bisa
 * menjadikannya tappable → lompat ke baris N di editor. Regex sengaja KETAT
 * (kelas jebakan: user print string berisi kata 'File'): pola lengkap dengan
 * kutip dan koma, dan pemanggil WAJIB memverifikasi file ada di workspace
 * sebelum menjadikannya link (bukan tanggung jawab parser).
 *
 * A6: hint NameError dari tabel statis alias super-populer. Bahasa hint
 * SELALU "Mungkin maksudmu…" — tidak pernah mengklaim pasti (klausul
 * kejujuran user 2026-08-18). Tabel sengaja pendek: hanya alias yang
 * konvensinya nyaris universal.
 */
object TracebackParser {

    // contoh sasaran:  File "/data/.../files/main.py", line 12, in <module>
    //                  File "main.py", line 3
    private val TRACEBACK = Regex("""File "([^"]+)", line (\d+)""")

    data class Hit(val rawPath: String, val fileName: String, val line: Int)

    /** Baris traceback → Hit, atau null bila bukan traceback. */
    fun parse(line: String): Hit? {
        val m = TRACEBACK.find(line) ?: return null
        val raw = m.groupValues[1]
        val n = m.groupValues[2].toIntOrNull() ?: return null
        if (n < 1) return null
        return Hit(rawPath = raw, fileName = raw.substringAfterLast('/'), line = n)
    }

    /** Hit hanya layak jadi link bila file-nya benar-benar ada di workspace. */
    fun isWorkspaceFile(hit: Hit, workspaceDir: File): Boolean =
        hit.fileName.endsWith(".py") && File(workspaceDir, hit.fileName).isFile

    // ---- A6: hint NameError ------------------------------------------------

    // alias → baris import yang lazim. HANYA konvensi yang nyaris universal.
    private val IMPORT_ALIASES = mapOf(
        "plt" to "import matplotlib.pyplot as plt",
        "np" to "import numpy as np",
        "pd" to "import pandas as pd",
        "sns" to "import seaborn as sns",
        "tf" to "import tensorflow as tf",
        "sk" to "import sklearn as sk",
        "requests" to "import requests",
        "json" to "import json",
        "os" to "import os",
        "sys" to "import sys",
        "math" to "import math",
        "random" to "import random",
        "re" to "import re",
        "time" to "import time",
        "datetime" to "import datetime",
    )

    private val NAME_ERROR = Regex("""NameError: name '([A-Za-z_][A-Za-z0-9_]*)' is not defined""")

    /**
     * Baris NameError → teks hint satu baris, atau null.
     * Tidak menyentuh network/state — murni tabel. Status paket terpasang
     * (bila relevan) ditambahkan PEMANGGIL via InstalledPackages, bukan di
     * sini, supaya parser tetap bebas-context dan gampang diuji.
     */
    fun nameErrorHint(line: String): String? {
        val m = NAME_ERROR.find(line) ?: return null
        val name = m.groupValues[1]
        val imp = IMPORT_ALIASES[name] ?: return null
        return "i Mungkin maksudmu: $imp"
    }

    /** Nama modul akar dari hint (utk cek terpasang): "plt" -> "matplotlib". */
    fun rootModuleForAlias(line: String): String? {
        val m = NAME_ERROR.find(line) ?: return null
        val imp = IMPORT_ALIASES[m.groupValues[1]] ?: return null
        return imp.removePrefix("import ").substringBefore(" ").substringBefore(".")
    }
}
