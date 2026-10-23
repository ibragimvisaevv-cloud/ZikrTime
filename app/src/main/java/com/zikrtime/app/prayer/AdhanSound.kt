package com.zikrtime.app.prayer

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri

/**
 * Встроенная запись азана (res/raw/adhan, лицензия CC0 — см. «О приложении»).
 * Ищем по имени, чтобы приложение собиралось и без файла: тогда используется мелодия будильника телефона.
 */
object AdhanSound {

    @SuppressLint("DiscouragedApi")
    fun resId(context: Context): Int = context.resources.getIdentifier("adhan", "raw", context.packageName)

    fun available(context: Context): Boolean = resId(context) != 0

    fun uri(context: Context): Uri? = resId(context).takeIf { it != 0 }?.let { Uri.parse("android.resource://${context.packageName}/$it") }
}
