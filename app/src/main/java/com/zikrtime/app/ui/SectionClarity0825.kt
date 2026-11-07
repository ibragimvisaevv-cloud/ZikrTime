package com.zikrtime.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration

/** Action-oriented labels; keep the same meaning in every supported language. */
enum class SectionClarity(vararg val translations: String) {
    Limits("Лимиты приложений", "App limits", "Ilova cheklovlari", "Маҳдудияти барномаҳо", "Қолданба шектеулері", "Колдонмо чектөөлөрү", "Tətbiq limitləri", "Uygulama sınırları"),
    LimitsIntro("Выберите приложения и общее время на день", "Choose apps and a shared daily allowance", "Ilovalar va umumiy kunlik vaqtni tanlang", "Барномаҳо ва вақти умумии рӯзонаро интихоб кунед", "Қолданбалар мен ортақ күндік уақытты таңдаңыз", "Колдонмолорду жана жалпы күндүк убакытты тандаңыз", "Tətbiqləri və ümumi gündəlik vaxtı seçin", "Uygulamaları ve ortak günlük süreyi seçin"),
    LimitMeaning("Один лимит на все выбранные приложения. Когда время закончится, доступ к ним закроется, если включены ограничения.", "One allowance for all selected apps. When time runs out, access is blocked if limits are enabled.", "Barcha tanlangan ilovalar uchun bitta limit. Cheklovlar yoqilgan bo'lsa, vaqt tugaganda kirish yopiladi.", "Як ҳад барои ҳамаи барномаҳои интихобшуда. Агар маҳдудиятҳо фаъол бошанд, пас аз анҷоми вақт дастрасӣ баста мешавад.", "Барлық таңдалған қолданбаға ортақ шек. Шектеулер қосулы болса, уақыт біткенде қолжетімділік жабылады.", "Бардык тандалган колдонмолорго жалпы чек. Чектөөлөр күйгүзүлсө, убакыт бүткөндө кирүү жабылат.", "Bütün seçilmiş tətbiqlər üçün bir limit. Limitlər aktivdirsə, vaxt bitəndə giriş bağlanır.", "Tüm seçili uygulamalar için tek süre. Sınırlar açıksa süre dolduğunda erişim kapanır."),
    DailyTime("Общее время на день", "Shared daily time", "Umumiy kunlik vaqt", "Вақти умумии рӯзона", "Ортақ күндік уақыт", "Жалпы күндүк убакыт", "Ümumi gündəlik vaxt", "Ortak günlük süre"),
    PhoneAccess("Разрешения телефона", "Phone permissions", "Telefon ruxsatlari", "Иҷозатҳои телефон", "Телефон рұқсаттары", "Телефондун уруксаттары", "Telefon icazələri", "Telefon izinleri"),
    SetPhoneAccess("Выдать разрешения", "Set up phone permissions", "Telefon ruxsatlarini sozlash", "Танзими иҷозатҳои телефон", "Телефон рұқсаттарын баптау", "Телефондун уруксаттарын жөндөө", "Telefon icazələrini qur", "Telefon izinlerini ayarla"),
    ProtectionInactive("Защита не активна — проверьте разрешения", "Protection is inactive — check phone permissions", "Himoya faol emas — telefon ruxsatlarini tekshiring", "Ҳифз фаъол нест — иҷозатҳои телефонро санҷед", "Қорғаныс белсенді емес — телефон рұқсаттарын тексеріңіз", "Коргоо активдүү эмес — телефондун уруксаттарын текшериңиз", "Qoruma aktiv deyil — telefon icazələrini yoxlayın", "Koruma etkin değil — telefon izinlerini kontrol edin"),
    AccessReason("Нужно, чтобы считать время и закрывать выбранные приложения. Каждое разрешение включается отдельно в настройках телефона.", "Needed to measure time and block selected apps. Enable each permission separately in phone settings.", "Vaqtni hisoblash va tanlangan ilovalarni cheklash uchun kerak. Har bir ruxsat telefon sozlamalarida alohida yoqiladi.", "Барои ҳисоб кардани вақт ва бастани барномаҳои интихобшуда лозим аст. Ҳар иҷозат дар танзимоти телефон ҷудогона фаъол мешавад.", "Уақытты есептеу және таңдалған қолданбаларды бұғаттау үшін қажет. Әр рұқсат телефон баптауларында бөлек беріледі.", "Убакытты эсептөө жана тандалган колдонмолорду бөгөттөө үчүн керек. Ар бир уруксат телефон жөндөөлөрүндө өзүнчө берилет.", "Vaxtı ölçmək və seçilmiş tətbiqləri bloklamaq üçün lazımdır. Hər icazəni telefon ayarlarında ayrıca aktivləşdirin.", "Süreyi ölçmek ve seçili uygulamaları engellemek için gerekir. Her izni telefon ayarlarında ayrı ayrı açın."),
    LimitSwitch("Ограничивать время", "Limit app time", "Ilova vaqtini cheklash", "Маҳдуд кардани вақт", "Қолданба уақытын шектеу", "Колдонмо убактысын чектөө", "Tətbiq vaxtını məhdudlaşdır", "Uygulama süresini sınırla"),
    PauseSwitch("Зикр перед открытием", "Dhikr before opening", "Ochishdan oldin zikr", "Зикр пеш аз кушодан", "Ашу алдында зікір", "Ачуудан мурун зикир", "Açmazdan əvvəl zikr", "Açmadan önce zikir"),
    Optional("Дополнительно — по желанию", "Optional extras", "Qo'shimcha — ixtiyoriy", "Иловагӣ — ихтиёрӣ", "Қосымша — қалауыңызша", "Кошумча — каалооңуз боюнча", "Əlavə — istəyə görə", "İsteğe bağlı ek seçenekler"),
    Questions("Ответить на вопросы", "Answer questions", "Savollarga javob berish", "Ҷавоб ба саволҳо", "Сұрақтарға жауап беру", "Суроолорго жооп берүү", "Suallara cavab ver", "Soruları yanıtla"),
    KnowledgeIntro("Коран, зикры и вопросы для обучения", "Quran, adhkar and learning questions", "Qur'on, zikrlar va bilim savollari", "Қуръон, зикрҳо ва саволҳои омӯзишӣ", "Құран, зікір және оқу сұрақтары", "Куран, зикир жана окуу суроолору", "Quran, zikrlər və öyrənmə sualları", "Kur'an, zikirler ve öğrenme soruları"),
    BonusMeaning("Бонус — это дополнительное время для приложений, выбранных в разделе «Лимиты». Учиться можно и без ограничения приложений.", "The bonus adds time for apps selected in App limits. You can learn without enabling app limits.", "Bonus «Ilova cheklovlari»da tanlangan ilovalar uchun qo'shimcha vaqt. Ilovalarni cheklamasdan ham o'rganish mumkin.", "Бонус барои барномаҳои дар «Маҳдудияти барномаҳо» интихобшуда вақти иловагӣ медиҳад. Бе маҳдуд кардани барномаҳо низ омӯхтан мумкин аст.", "Бонус «Қолданба шектеулері» бөлімінде таңдалған қолданбаларға қосымша уақыт береді. Шектеулерсіз де оқуға болады.", "Бонус «Колдонмо чектөөлөрү» бөлүмүндө тандалган колдонмолорго кошумча убакыт берет. Чектөөлөрсүз да окусаңыз болот.", "Bonus «Tətbiq limitləri»ndə seçilmiş tətbiqlərə əlavə vaxt verir. Limitləri açmadan da öyrənə bilərsiniz.", "Bonus, Uygulama sınırları'nda seçili uygulamalara ek süre verir. Sınırları açmadan da öğrenebilirsiniz."),
    Review("Повторить изученное", "Review what you learned", "O'rganilganlarni takrorlash", "Такрори омӯхтаҳо", "Үйренгенді қайталау", "Үйрөнгөндү кайталоо", "Öyrəndiklərini təkrar et", "Öğrendiklerini tekrarla"),
    Settings("Настройки", "Settings", "Sozlamalar", "Танзимот", "Баптаулар", "Жөндөөлөр", "Ayarlar", "Ayarlar"),
    SettingsIntro("Город, уведомления и оформление", "City, notifications and appearance", "Shahar, bildirishnomalar va ko'rinish", "Шаҳр, огоҳиҳо ва намуди барнома", "Қала, хабарландырулар және көрініс", "Шаар, билдирүүлөр жана көрүнүш", "Şəhər, bildirişlər və görünüş", "Şehir, bildirimler ve görünüm"),
    Appearance("Оформление", "Appearance", "Ko'rinish", "Намуди барнома", "Көрініс", "Көрүнүш", "Görünüş", "Görünüm"),
    OtherSections("Другие разделы", "Other sections", "Boshqa bo'limlar", "Бахшҳои дигар", "Басқа бөлімдер", "Башка бөлүмдөр", "Digər bölmələr", "Diğer bölümler"),
    QuranHint("Суры, перевод, закладки и заметки", "Surahs, translation, bookmarks and notes", "Suralar, tarjima, xatcho'plar va qaydlar", "Сураҳо, тарҷума, хатчӯбҳо ва ёддоштҳо", "Сүрелер, аударма, бетбелгілер мен жазбалар", "Сүрөлөр, котормо, кыстармалар жана жазуулар", "Surələr, tərcümə, əlfəcinlər və qeydlər", "Sureler, çeviri, yer imleri ve notlar"),
    TasbihHint("Счётчик повторений зикра", "Count your dhikr repetitions", "Zikr takrorlarini sanash", "Ҳисоб кардани такрори зикр", "Зікір қайталауларын санау", "Зикир кайталоолорун эсептөө", "Zikr təkrarlarını say", "Zikir tekrarlarını say"),
    QiblaHint("Найти направление для намаза", "Find the direction for prayer", "Namoz yo'nalishini topish", "Ёфтани самти намоз", "Намаз бағытын табу", "Намаз багытын табуу", "Namaz istiqamətini tap", "Namaz yönünü bul"),
    PlacesHint("Мечети рядом и маршрут до них", "Nearby mosques and directions", "Yaqin masjidlar va ularga yo'l", "Масҷидҳои наздик ва роҳ ба онҳо", "Жақын мешіттер және оларға бағыт", "Жакынкы мечиттер жана аларга жол", "Yaxın məscidlər və marşrut", "Yakındaki camiler ve yol tarifi"),
    RamadanHint("Время сухура и ифтара", "Suhoor and iftar times", "Saharlik va iftor vaqtlari", "Вақти саҳарӣ ва ифтор", "Сәресі мен ауызашар уақыты", "Саарлык жана ооз ачуу убактысы", "Sahur və iftar vaxtları", "Sahur ve iftar vakitleri"),
    ZakatHint("Рассчитать сумму закята", "Calculate a zakat amount", "Zakot miqdorini hisoblash", "Ҳисоб кардани маблағи закот", "Зекет сомасын есептеу", "Зекет суммасын эсептөө", "Zəkat məbləğini hesabla", "Zekât tutarını hesapla"),
    ProgressHint("Посмотреть сохранённую статистику", "View your saved statistics", "Saqlangan statistikani ko'rish", "Дидани омори сабтшуда", "Сақталған статистиканы көру", "Сакталган статистиканы көрүү", "Saxlanmış statistikaya bax", "Kaydedilmiş istatistikleri gör"),
}

@Composable
fun sectionCopy(key: SectionClarity): String {
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
