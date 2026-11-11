package com.blackout.identifier.data

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf

/** Your settings. Stored only on this phone. */
object Prefs {
    private lateinit var sp: SharedPreferences

    private val _iconStyle = mutableStateOf("A")
    private val _iconMain = mutableIntStateOf(0xFFE5484D.toInt())
    private val _iconSecond = mutableIntStateOf(0xFFFFFFFF.toInt())

    fun init(context: Context) {
        sp = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
        _iconStyle.value = sp.getString("iconStyle", "A") ?: "A"
        _iconMain.intValue = sp.getInt("iconMain", 0xFFE5484D.toInt())
        _iconSecond.intValue = sp.getInt("iconSecond", 0xFFFFFFFF.toInt())
    }

    var iconStyle: String get() = _iconStyle.value; set(v) { _iconStyle.value = v; sp.edit().putString("iconStyle", v).apply() }
    var iconMain: Int get() = _iconMain.intValue; set(v) { _iconMain.intValue = v; sp.edit().putInt("iconMain", v).apply() }
    var iconSecond: Int get() = _iconSecond.intValue; set(v) { _iconSecond.intValue = v; sp.edit().putInt("iconSecond", v).apply() }
    var monitorOn: Boolean get() = sp.getBoolean("monitorOn", false); set(v) { sp.edit().putBoolean("monitorOn", v).apply() }
}
