package com.zikrtime.app.i18n

import android.app.Activity
import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import java.util.Locale

/**
 * Язык приложения. По умолчанию — язык телефона; пользователь может выбрать другой в настройках.
 * Выбор хранится в тех же настройках, что и всё остальное, и применяется к каждому экрану,
 * уведомлению и будильнику.
 */
object Lang {

    data class Language(val code: String, val nativeName: String)

    /** Поддерживаемые языки. Названия — так, как их пишут носители языка. */
    val supported = listOf(
        Language("ru", "Русский"),
        Language("en", "English"),
        Language("uz", "Oʻzbekcha"),
        Language("tg", "Тоҷикӣ"),
        Language("kk", "Қазақша"),
        Language("ky", "Кыргызча"),
        Language("az", "Azərbaycanca"),
        Language("tr", "Türkçe"),
    )

    private val codes = supported.map { it.code }.toSet()
    private const val PREFS = "zikrtime"
    private const val KEY = "language"

    /** Выбранный пользователем язык или "" — как в системе. */
    fun saved(context: Context): String {
        if (Build.VERSION.SDK_INT >= 33) {
            // На Android 13+ язык приложения можно поменять и в системных настройках — там и берём.
            val list = runCatching { context.getSystemService(LocaleManager::class.java)?.applicationLocales }.getOrNull()
            if (list != null) {
                val code = if (list.isEmpty) "" else list[0].language
                return if (code.isEmpty() || code in codes) code else ""
            }
        }
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "") ?: ""
    }

    /** Язык, на котором показываем содержимое (вопросы, города): выбранный или системный, иначе английский. */
    fun content(context: Context): String {
        val chosen = saved(context)
        if (chosen.isNotEmpty()) return chosen
        val system = systemLocale().language
        return if (system in codes) system else "en"
    }

    private fun systemLocale(): Locale = LocaleList.getDefault()[0] ?: Locale.getDefault()

    /** Контекст с выбранным языком — для экранов (attachBaseContext), уведомлений и служб. */
    fun wrap(base: Context): Context {
        val code = saved(base)
        if (code.isEmpty()) return base
        val locale = Locale.forLanguageTag(code)
        val config = Configuration(base.resources.configuration)
        config.setLocale(locale)
        config.setLayoutDirection(locale)
        return base.createConfigurationContext(config)
    }

    /** Сменить язык и перезапустить экран. Пустая строка — как в системе. */
    fun set(activity: Activity, code: String) {
        activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, code).commit()
        if (Build.VERSION.SDK_INT >= 33) {
            // На Android 13+ язык виден и в системных настройках приложения.
            runCatching {
                activity.getSystemService(LocaleManager::class.java).applicationLocales =
                    if (code.isEmpty()) LocaleList.getEmptyLocaleList() else LocaleList.forLanguageTags(code)
            }
        }
        activity.recreate()
    }

    /** Текущая локаль контекста — для форматирования дат. */
    fun locale(context: Context): Locale = context.resources.configuration.locales[0] ?: Locale.getDefault()
}
