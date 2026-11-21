package com.zikrtime.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import com.zikrtime.app.data.Store

/** Task-oriented labels; stored routes and user preferences remain unchanged. */
enum class UiClarity0825(vararg val translations: String) {
    Limits("Лимиты", "Limits", "Limitlar", "Ҳудудҳо", "Шектеулер", "Чектөөлөр", "Limitlər", "Sınırlar"),
    Settings("Настройки", "Settings", "Sozlamalar", "Танзимот", "Баптаулар", "Жөндөөлөр", "Ayarlar", "Ayarlar"),
    ChangeCity("Изменить город", "Change city", "Shaharni o‘zgartirish", "Иваз кардани шаҳр", "Қаланы өзгерту", "Шаарды өзгөртүү", "Şəhəri dəyiş", "Şehri değiştir"),
    Reading("Напоминания", "Reminders", "Eslatmalar", "Ёдраскуниҳо", "Еске салулар", "Эскертүүлөр", "Xatırlatmalar", "Hatırlatmalar"),
    PrayerHistory("История намазов", "Prayer history", "Namozlar tarixi", "Таърихи намозҳо", "Намаз тарихы", "Намаз тарыхы", "Namaz tarixçəsi", "Namaz geçmişi"),
    PrayerAlerts("Азан и уведомления", "Adhan and alerts", "Azon va bildirishnomalar", "Азон ва огоҳиҳо", "Азан және хабарламалар", "Азан жана билдирүүлөр", "Azan və bildirişlər", "Ezan ve bildirimler"),
    MarkPrayerHint("Когда совершите намаз, нажмите кружок справа.", "After praying, tap the circle on the right.", "Namozdan so‘ng o‘ngdagi doirani bosing.", "Пас аз намоз доираи тарафи ростро пахш кунед.", "Намаздан кейін оң жақтағы шеңберді басыңыз.", "Намаздан кийин оң жактагы тегеректи басыңыз.", "Namazdan sonra sağdakı dairəyə toxun.", "Namazdan sonra sağdaki daireye dokunun."),
    ReadQuran("Читать Коран", "Read Quran", "Qur’onni o‘qish", "Хондани Қуръон", "Құран оқу", "Куран окуу", "Quran oxu", "Kur’an oku"),
    QuranHint("Суры, перевод и закладки", "Surahs, translation and bookmarks", "Suralar, tarjima va xatcho‘plar", "Сураҳо, тарҷума ва хатчӯбҳо", "Сүрелер, аударма және бетбелгілер", "Сүрөлөр, котормо жана кыстармалар", "Surələr, tərcümə və əlfəcinlər", "Sureler, çeviri ve yer imleri"),
    ReadingMoments("Перед сном и в пятницу", "Bedtime and Friday reading", "Uyqudan oldin va juma kuni", "Пеш аз хоб ва рӯзи ҷумъа", "Ұйықтар алдында және жұмада", "Уктаар алдында жана жума күнү", "Yatmazdan əvvəl və cümə günü", "Yatmadan önce ve cuma günü"),
    ReadingHint("Что почитать и когда напомнить", "What to read and when to remind you", "Nima o‘qish va qachon eslatish", "Чиро хондан ва кай ёдрас кардан", "Не оқу және қашан еске салу", "Эмне окуу жана качан эске салуу", "Nə oxumaq və nə vaxt xatırlatmaq", "Ne okunmalı, ne zaman hatırlatılmalı"),
    Questions("Вопросы об исламе", "Questions about Islam", "Islom haqida savollar", "Саволҳо дар бораи ислом", "Ислам туралы сұрақтар", "Ислам жөнүндө суроолор", "İslam haqqında suallar", "İslam hakkında sorular"),
    QuestionsHint("Проверяйте знания по своему уровню.", "Test your knowledge at your own level.", "O‘z darajangizda bilimingizni sinang.", "Дониши худро мувофиқи сатҳатон санҷед.", "Өз деңгейіңіздегі біліміңізді тексеріңіз.", "Өз деңгээлиңизде билимиңизди текшериңиз.", "Öz səviyyəndə biliyini yoxla.", "Kendi seviyenizde bilginizi sınayın."),
    StartDhikr("Начать зикр", "Start dhikr", "Zikrni boshlash", "Оғози зикр", "Зікірді бастау", "Зикирди баштоо", "Zikrə başla", "Zikre başla"),
    FindQibla("Найти направление кыблы", "Find the qibla direction", "Qibla yo‘nalishini topish", "Ёфтани самти қибла", "Құбыла бағытын табу", "Кыбыла багытын табуу", "Qiblə istiqamətini tap", "Kıble yönünü bul"),
    QiblaHint("Поверните телефон по подсказке компаса", "Turn your phone using the compass guide", "Kompas ko‘rsatmasiga qarab telefonni aylantiring", "Телефонро аз рӯи роҳнамои қутбнамо гардонед", "Телефонды компас нұсқауымен бұрыңыз", "Телефонду компастын көрсөтмөсү менен буруңуз", "Kompasın göstərişi ilə telefonu çevir", "Pusula rehberine göre telefonunuzu çevirin"),
    AlarmOffHint("Выключен · Нажмите, чтобы настроить", "Off · Tap to set up", "O‘chirilgan · Sozlash uchun bosing", "Хомӯш · Барои танзим пахш кунед", "Өшірулі · Баптау үшін басыңыз", "Өчүк · Жөндөө үчүн басыңыз", "Sönülüdür · Qurmaq üçün toxun", "Kapalı · Ayarlamak için dokunun"),
    AppLimits("Блокировка соцсетей", "Social media limits", "Ijtimoiy tarmoqlar limiti", "Маҳдудияти шабакаҳои иҷтимоӣ", "Әлеуметтік желілерге шектеу", "Социалдык тармактарга чектөө", "Sosial şəbəkələr üçün limit", "Sosyal medya sınırı"),
    AppLimitsHint(
        "Когда дневной лимит закончится, выбранные приложения заблокируются. %d верных ответа об исламе дадут ещё %d минут.",
        "When your daily time runs out, the apps you chose are blocked. Answer %d questions about Islam correctly to get %d more minutes.",
        "Kunlik vaqt tugaganda tanlangan ilovalar bloklanadi. Islom haqida %d ta savolga to‘g‘ri javob berib, yana %d daqiqa oling.",
        "Вақте ки вақти рӯзона тамом шуд, барномаҳои интихобшуда баста мешаванд. Ба %d савол дар бораи ислом дуруст ҷавоб диҳед ва боз %d дақиқа гиред.",
        "Күндік уақыт біткенде таңдаған қолданбаларыңыз бұғатталады. Ислам туралы %d сұраққа дұрыс жауап берсеңіз, тағы %d минут аласыз.",
        "Күнүмдүк убакыт бүткөндө тандалган колдонмолор бөгөттөлөт. Ислам жөнүндө %d суроого туура жооп берип, дагы %d мүнөт алыңыз.",
        "Gündəlik vaxt bitəndə seçdiyiniz tətbiqlər bloklanır. İslam haqqında %d suala düzgün cavab verib daha %d dəqiqə qazanın.",
        "Günlük süre dolduğunda seçtiğiniz uygulamalar engellenir. İslam hakkında %d soruyu doğru yanıtlayarak %d dakika daha kazanın.",
    ),
    SetLimit("Настроить лимит", "Set up a limit", "Limitni sozlash", "Танзими маҳдудият", "Шектеуді баптау", "Чектөөнү жөндөө", "Limiti qur", "Sınır ayarla"),
}

@Composable
fun focusLimitExplanation(): String = clarityCopy(UiClarity0825.AppLimitsHint).format(
    LocalConfiguration.current.locales[0], Store.CORRECT_ANSWERS_PER_ROUND, Store.BONUS_MINUTES_PER_ROUND,
)

@Composable
fun clarityCopy(key: UiClarity0825): String {
    val language = LocalConfiguration.current.locales[0]?.language ?: "en"
    return key.translations[when (language) {
        "ru" -> 0; "uz" -> 2; "tg" -> 3; "kk" -> 4; "ky" -> 5; "az" -> 6; "tr" -> 7; else -> 1
    }]
}
