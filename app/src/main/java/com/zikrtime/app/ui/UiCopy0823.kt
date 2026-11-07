package com.zikrtime.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration

/** Short action labels shared by the focus, learning and settings screens. */
enum class UiCopy0823(vararg val translations: String) {
    ChooseApps("Выбрать приложения", "Choose apps", "Ilovalarni tanlash", "Интихоби барномаҳо", "Қолданбаларды таңдау", "Колдонмолорду тандоо", "Tətbiqləri seç", "Uygulamaları seç"),
    ConfigureLimits("Настроить ограничения", "Set up limits", "Cheklovlarni sozlash", "Танзими маҳдудиятҳо", "Шектеулерді баптау", "Чектөөлөрдү жөндөө", "Məhdudiyyətləri qur", "Sınırları ayarla"),
    FocusIntro("Выбери приложения и время, которое хочешь проводить в них каждый день.", "Choose apps and the time you want to spend in them each day.", "Ilovalarni va ularda har kuni o'tkazmoqchi bo'lgan vaqtingizni tanlang.", "Барномаҳо ва вақтеро интихоб кунед, ки ҳар рӯз дар онҳо гузарондан мехоҳед.", "Қолданбаларды және оларға күн сайын бөлетін уақытты таңдаңыз.", "Колдонмолорду жана аларга күн сайын бөлө турган убакытты тандаңыз.", "Tətbiqləri və hər gün onlara ayırmaq istədiyin vaxtı seç.", "Uygulamaları ve her gün onlara ayırmak istediğin süreyi seç."),
    NeedApps("Сначала выбери приложения", "Choose apps first", "Avval ilovalarni tanlang", "Аввал барномаҳоро интихоб кунед", "Алдымен қолданбаларды таңдаңыз", "Алгач колдонмолорду тандаңыз", "Əvvəlcə tətbiqləri seç", "Önce uygulamaları seç"),
    NeedAccess("Нужны разрешения телефона", "Phone permissions needed", "Telefon ruxsatlari kerak", "Иҷозатҳои телефон лозиманд", "Телефон рұқсаттары қажет", "Телефондун уруксаттары керек", "Telefon icazələri lazımdır", "Telefon izinleri gerekiyor"),
    FocusOff("Ограничения выключены", "Limits are off", "Cheklovlar o'chirilgan", "Маҳдудиятҳо хомӯшанд", "Шектеулер өшірулі", "Чектөөлөр өчүк", "Məhdudiyyətlər söndürülüb", "Sınırlar kapalı"),
    FocusReady("Настройка завершена", "Setup complete", "Sozlash tugallandi", "Танзим анҷом ёфт", "Баптау аяқталды", "Жөндөө аяктады", "Quraşdırma tamamlandı", "Kurulum tamamlandı"),
    SelectedApps("Выбрано приложений", "Apps selected", "Tanlangan ilovalar", "Барномаҳои интихобшуда", "Таңдалған қолданбалар", "Тандалган колдонмолор", "Seçilmiş tətbiqlər", "Seçili uygulamalar"),
    SelectionSaved("Изменения сохраняются сразу. Ограничения действуют на все отмеченные приложения.", "Changes are saved immediately. Limits apply to all checked apps.", "O'zgarishlar darhol saqlanadi. Cheklovlar belgilangan barcha ilovalarga tegishli.", "Тағйирот фавран сабт мешавад. Маҳдудиятҳо ба ҳамаи барномаҳои интихобшуда дахл доранд.", "Өзгерістер бірден сақталады. Шектеулер белгіленген барлық қолданбаға қолданылады.", "Өзгөртүүлөр дароо сакталат. Чектөөлөр белгиленген бардык колдонмолорго колдонулат.", "Dəyişikliklər dərhal saxlanılır. Məhdudiyyətlər bütün seçilmiş tətbiqlərə aiddir.", "Değişiklikler hemen kaydedilir. Sınırlar tüm seçili uygulamalar için geçerlidir."),
    SetupIntro("Настройки сохраняются сразу. Выбери приложения, задай дневной лимит и включи нужные разрешения.", "Settings save immediately. Choose apps, set a daily limit and enable the required permissions.", "Sozlamalar darhol saqlanadi. Ilovalarni tanlang, kunlik limitni belgilang va kerakli ruxsatlarni yoqing.", "Танзимот фавран сабт мешавад. Барномаҳоро интихоб кунед, ҳадди рӯзонаро гузоред ва иҷозатҳои заруриро фаъол кунед.", "Баптаулар бірден сақталады. Қолданбаларды таңдаңыз, күндік шекті қойып, қажет рұқсаттарды беріңіз.", "Жөндөөлөр дароо сакталат. Колдонмолорду тандап, күндүк чекти коюңуз жана керектүү уруксаттарды бериңиз.", "Ayarlar dərhal saxlanılır. Tətbiqləri seç, gündəlik limit təyin et və lazımi icazələri ver.", "Ayarlar hemen kaydedilir. Uygulamaları seç, günlük sınır belirle ve gerekli izinleri ver."),
    PauseOptional("Пауза перед открытием приложения", "Pause before opening an app", "Ilovani ochishdan oldin tanaffus", "Таваққуф пеш аз кушодани барнома", "Қолданбаны ашу алдындағы үзіліс", "Колдонмону ачар алдындагы тыныгуу", "Tətbiqi açmazdan əvvəl fasilə", "Uygulamayı açmadan önce mola"),
    StudyIntro("Выбери свой уровень. Можно сначала прочитать объяснения, затем ответить на вопросы.", "Choose your level. You can read explanations first, then answer questions.", "Darajangizni tanlang. Avval izohlarni o'qib, keyin savollarga javob berishingiz mumkin.", "Сатҳи худро интихоб кунед. Аввал метавонед шарҳҳоро хонед, сипас ба саволҳо ҷавоб диҳед.", "Деңгейіңізді таңдаңыз. Алдымен түсіндірмелерді оқып, кейін сұрақтарға жауап беруге болады.", "Деңгээлиңизди тандаңыз. Адегенде түшүндүрмөлөрдү окуп, андан соң суроолорго жооп берсеңиз болот.", "Səviyyəni seç. Əvvəlcə izahları oxuyub, sonra suallara cavab verə bilərsən.", "Seviyeni seç. Önce açıklamaları okuyabilir, ardından soruları yanıtlayabilirsin."),
    ReadAndRemember("Читать и вспоминать", "Read and remember", "O'qish va eslash", "Хондан ва ёдоварӣ", "Оқу және еске түсіру", "Окуу жана эске салуу", "Oxu və xatırla", "Oku ve hatırla"),
    PracticeKnowledge("Проверить знания", "Test your knowledge", "Bilimni tekshirish", "Санҷиши дониш", "Білімді тексеру", "Билимди текшерүү", "Biliyini yoxla", "Bilgini sına"),
    TopicHint("Нажми на тему, чтобы начать вопросы только по ней.", "Tap a topic to start questions about it.", "Faqat shu mavzudagi savollarni boshlash uchun mavzuni bosing.", "Барои саволҳо аз як мавзӯъ, онро пахш кунед.", "Сол тақырыптағы сұрақтарды бастау үшін тақырыпты басыңыз.", "Ошол темадагы суроолорду баштоо үчүн теманы басыңыз.", "Yalnız həmin mövzudan suallar üçün mövzuya toxun.", "Yalnızca o konudan sorular için bir konuya dokun."),
    PrayerSettings("Намаз и напоминания", "Prayer and reminders", "Namoz va eslatmalar", "Намоз ва ёдрасҳо", "Намаз және еске салғыштар", "Намаз жана эскертүүлөр", "Namaz və xatırlatmalar", "Namaz ve hatırlatmalar"),
    PrayerSettingsHint("Расписание, азан и уведомления", "Schedule, adhan and notifications", "Jadval, azon va bildirishnomalar", "Ҷадвал, азон ва огоҳиҳо", "Кесте, азан және хабарландырулар", "Жадыбал, азан жана билдирүүлөр", "Cədvəl, azan və bildirişlər", "Vakitler, ezan ve bildirimler"),
    CalculationOptions("Параметры расчёта", "Calculation options", "Hisoblash parametrlari", "Параметрҳои ҳисоб", "Есептеу параметрлері", "Эсептөө параметрлери", "Hesablama seçimləri", "Hesaplama seçenekleri"),
    FocusSettingsHint("Приложения, дневной лимит и пауза на намаз", "Apps, daily limit and prayer pause", "Ilovalar, kunlik limit va namoz tanaffusi", "Барномаҳо, ҳадди рӯзона ва таваққуфи намоз", "Қолданбалар, күндік шек және намаз үзілісі", "Колдонмолор, күндүк чек жана намаз тыныгуусу", "Tətbiqlər, gündəlik limit və namaz fasiləsi", "Uygulamalar, günlük sınır ve namaz molası"),
    HelpAndSupport("Помощь и поддержка", "Help and support", "Yordam va qo'llab-quvvatlash", "Кумак ва дастгирӣ", "Көмек және қолдау", "Жардам жана колдоо", "Kömək və dəstək", "Yardım ve destek"),
    PermissionHint("Нажми «Открыть настройки», включи указанный доступ для ZikrTime и вернись сюда.", "Tap Open settings, enable the named access for ZikrTime, then return here.", "Sozlamalarni ochish tugmasini bosing, ZikrTime uchun ko'rsatilgan ruxsatni yoqing va qayting.", "Кушодани танзимотро пахш кунед, дастрасии номбаршударо барои ZikrTime фаъол кунед ва баргардед.", "Баптауларды ашу түймесін басып, ZikrTime үшін көрсетілген рұқсатты беріңіз де, осында оралыңыз.", "Жөндөөлөрдү ачуу баскычын басып, ZikrTime үчүн көрсөтүлгөн уруксатты берип, кайра кайтыңыз.", "Ayarları aç düyməsinə toxun, ZikrTime üçün göstərilən icazəni ver və bura qayıt.", "Ayarları aç'a dokun, ZikrTime için belirtilen izni etkinleştir ve buraya dön."),
}

@Composable
fun uiCopy(key: UiCopy0823): String {
    val language = LocalConfiguration.current.locales[0]?.language ?: "en"
    val index = when (language) {
        "ru" -> 0
        "uz" -> 2
        "tg" -> 3
        "kk" -> 4
        "ky" -> 5
        "az" -> 6
        "tr" -> 7
        else -> 1
    }
    return key.translations[index]
}
