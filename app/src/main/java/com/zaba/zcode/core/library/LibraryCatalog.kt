package com.zaba.zcode.core.library

import android.content.Context
import org.json.JSONArray
import java.io.InputStreamReader

enum class Compatibility {
    RECOMMENDED, // ✅
    HEAVY,       // ⚠️
    UNSUPPORTED  // ❌
}

data class LibraryItem(
    val name: String,
    val category: String,
    val kind: String,
    val supportedAbis: List<String>,
    val minAndroid: Int,
    val ramMbHint: Int,
    val heavyOnLowEnd: Boolean,
    val relevance: String,
    val summary: String,
    val note: String,
    val installName: String,
    val compatibility: Compatibility
)

object LibraryCatalog {
    private var cachedItems: List<LibraryItem>? = null

    fun load(context: Context): List<LibraryItem> {
        if (cachedItems != null) return cachedItems!!

        val list = mutableListOf<LibraryItem>()
        try {
            val stream = context.assets.open("libraries.json")
            val reader = InputStreamReader(stream, "UTF-8")
            val jsonText = reader.readText()
            reader.close()

            val array = JSONArray(jsonText)
            val isLow = DeviceProbe.isLowRam(context)
            val abis = DeviceProbe.getSupportedAbis()

            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val name = obj.getString("name")
                val category = obj.getString("category")
                val kind = obj.getString("kind")
                val abisArray = obj.getJSONArray("supported_abis")
                val supportedAbisList = mutableListOf<String>()
                for (j in 0 until abisArray.length()) {
                    supportedAbisList.add(abisArray.getString(j))
                }
                val minAndroid = obj.optInt("min_android", 26)
                val ramMbHint = obj.optInt("ram_mb_hint", 64)
                val heavyOnLowEnd = obj.optBoolean("heavy_on_low_end", false)
                val relevance = obj.optString("relevance", "recommended")
                val summary = obj.getString("summary")
                val note = obj.getString("note")
                val installName = obj.optString("install_name", name)

                // Tentukan kompatibilitas
                var comp = Compatibility.RECOMMENDED
                if (relevance == "unsupported") {
                    comp = Compatibility.UNSUPPORTED
                } else if (kind == "native" && supportedAbisList.isNotEmpty()) {
                    val hasCommonAbi = abis.any { it in supportedAbisList }
                    if (!hasCommonAbi) {
                        comp = Compatibility.UNSUPPORTED
                    }
                }

                if (comp != Compatibility.UNSUPPORTED) {
                    if (heavyOnLowEnd && isLow) {
                        comp = Compatibility.HEAVY
                    } else if (relevance == "heavy") {
                        comp = Compatibility.HEAVY
                    }
                }

                list.add(
                    LibraryItem(
                        name = name,
                        category = category,
                        kind = kind,
                        supportedAbis = supportedAbisList,
                        minAndroid = minAndroid,
                        ramMbHint = ramMbHint,
                        heavyOnLowEnd = heavyOnLowEnd,
                        relevance = relevance,
                        summary = summary,
                        note = note,
                        installName = installName,
                        compatibility = comp
                    )
                )
            }
        } catch (e: Exception) {
            // ignore
        }
        cachedItems = list
        return list
    }
}
