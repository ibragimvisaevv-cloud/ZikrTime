package com.zikrtime.app.data

/** Offline, source-linked selection; not an exhaustive list of morning/evening remembrances.
 * A target of 1 means one reading when the cited narration does not specify repetitions.
 * Translations explain the meaning; transliterations are reading aids, not the Arabic original.
 */
data class AzkarEntry(
    val id: String,
    val titleRu: String,
    val titleEn: String,
    val arabic: String,
    val transliterationRu: String,
    val transliterationEn: String,
    val meaningRu: String,
    val meaningEn: String,
    val repetitions: Int,
    val sourceLabel: String,
    val sourceUrl: String,
    val countIsPrescribed: Boolean = true,
)

object AzkarContent {
    const val VERSION = 1

    private val morningOpening = AzkarEntry(
        id = "morning_kingdom",
        titleRu = "О благе наступившего дня",
        titleEn = "Asking for the good of the day",
        arabic = "أَصْبَحْنَا وَأَصْبَحَ الْمُلْكُ لِلَّهِ وَالْحَمْدُ لِلَّهِ، لَا إِلَهَ إِلَّا اللَّهُ وَحْدَهُ لَا شَرِيكَ لَهُ، لَهُ الْمُلْكُ وَلَهُ الْحَمْدُ وَهُوَ عَلَى كُلِّ شَيْءٍ قَدِيرٌ. رَبِّ أَسْأَلُكَ خَيْرَ مَا فِي هَذَا الْيَوْمِ وَخَيْرَ مَا بَعْدَهُ، وَأَعُوذُ بِكَ مِنْ شَرِّ مَا فِي هَذَا الْيَوْمِ وَشَرِّ مَا بَعْدَهُ. رَبِّ أَعُوذُ بِكَ مِنَ الْكَسَلِ وَسُوءِ الْكِبَرِ، رَبِّ أَعُوذُ بِكَ مِنْ عَذَابٍ فِي النَّارِ وَعَذَابٍ فِي الْقَبْرِ.",
        transliterationRu = "Асбахна ва асбахаль-мульку лиллях, валь-хамду лиллях. Ля иляха илляллаху вахдаху ля шарика лях, ляхуль-мульку ва ляхуль-хамду ва хува ‘аля кулли шай’ин кадир. Рабби ас’алюка хайра ма фи хазаль-яуми ва хайра ма ба‘дах, ва а‘узу бика мин шарри ма фи хазаль-яуми ва шарри ма ба‘дах. Рабби а‘узу бика миналь-касали ва су’иль-кибар. Рабби а‘узу бика мин ‘азабин фин-нари ва ‘азабин филь-кабр.",
        transliterationEn = "Aṣbaḥnā wa aṣbaḥal-mulku lillāh, wal-ḥamdu lillāh. Lā ilāha illallāhu waḥdahu lā sharīka lah, lahul-mulku wa lahul-ḥamdu wa huwa ‘alā kulli shay’in qadīr. Rabbi as’aluka khayra mā fī hādhal-yawmi wa khayra mā ba‘dah, wa a‘ūdhu bika min sharri mā fī hādhal-yawmi wa sharri mā ba‘dah. Rabbi a‘ūdhu bika minal-kasali wa sū’il-kibar. Rabbi a‘ūdhu bika min ‘adhābin fin-nāri wa ‘adhābin fil-qabr.",
        meaningRu = "Мы встретили утро, и вся власть принадлежит Аллаху. Хвала Аллаху! Нет божества, достойного поклонения, кроме одного Аллаха, у Которого нет сотоварища. Ему принадлежат власть и хвала, и Он способен на всякую вещь. Господь мой, прошу Тебя о благе этого дня и последующего времени и ищу у Тебя защиты от их зла. Господь мой, защити меня от лени и тягот дряхлости. Господь мой, ищу у Тебя защиты от мучений Огня и могилы.",
        meaningEn = "We have reached the morning, and all dominion belongs to Allah. All praise is His. None is worthy of worship except Allah alone, without a partner. His are dominion and praise, and He has power over all things. My Lord, I ask You for the good in this day and what follows, and seek Your protection from their evil. My Lord, protect me from laziness and the hardship of decrepit old age. Protect me from punishment in the Fire and in the grave.",
        repetitions = 1,
        countIsPrescribed = false,
        sourceLabel = "Muslim 2723b · Hisn al-Muslim 77",
        sourceUrl = "https://sunnah.com/muslim:2723b",
    )

    private val eveningOpening = AzkarEntry(
        id = "evening_kingdom",
        titleRu = "О благе наступившей ночи",
        titleEn = "Asking for the good of the night",
        arabic = "أَمْسَيْنَا وَأَمْسَى الْمُلْكُ لِلَّهِ وَالْحَمْدُ لِلَّهِ، لَا إِلَهَ إِلَّا اللَّهُ وَحْدَهُ لَا شَرِيكَ لَهُ، لَهُ الْمُلْكُ وَلَهُ الْحَمْدُ وَهُوَ عَلَى كُلِّ شَيْءٍ قَدِيرٌ. رَبِّ أَسْأَلُكَ خَيْرَ مَا فِي هَذِهِ اللَّيْلَةِ وَخَيْرَ مَا بَعْدَهَا، وَأَعُوذُ بِكَ مِنْ شَرِّ مَا فِي هَذِهِ اللَّيْلَةِ وَشَرِّ مَا بَعْدَهَا. رَبِّ أَعُوذُ بِكَ مِنَ الْكَسَلِ وَسُوءِ الْكِبَرِ، رَبِّ أَعُوذُ بِكَ مِنْ عَذَابٍ فِي النَّارِ وَعَذَابٍ فِي الْقَبْرِ.",
        transliterationRu = "Амсайна ва амсаль-мульку лиллях, валь-хамду лиллях. Ля иляха илляллаху вахдаху ля шарика лях, ляхуль-мульку ва ляхуль-хамду ва хува ‘аля кулли шай’ин кадир. Рабби ас’алюка хайра ма фи хазихиль-ляйляти ва хайра ма ба‘даха, ва а‘узу бика мин шарри ма фи хазихиль-ляйляти ва шарри ма ба‘даха. Рабби а‘узу бика миналь-касали ва су’иль-кибар. Рабби а‘узу бика мин ‘азабин фин-нари ва ‘азабин филь-кабр.",
        transliterationEn = "Amsaynā wa amsal-mulku lillāh, wal-ḥamdu lillāh. Lā ilāha illallāhu waḥdahu lā sharīka lah, lahul-mulku wa lahul-ḥamdu wa huwa ‘alā kulli shay’in qadīr. Rabbi as’aluka khayra mā fī hādhihil-laylati wa khayra mā ba‘dahā, wa a‘ūdhu bika min sharri mā fī hādhihil-laylati wa sharri mā ba‘dahā. Rabbi a‘ūdhu bika minal-kasali wa sū’il-kibar. Rabbi a‘ūdhu bika min ‘adhābin fin-nāri wa ‘adhābin fil-qabr.",
        meaningRu = "Мы встретили вечер, и вся власть принадлежит Аллаху. Хвала Аллаху! Нет божества, достойного поклонения, кроме одного Аллаха, у Которого нет сотоварища. Ему принадлежат власть и хвала, и Он способен на всякую вещь. Господь мой, прошу Тебя о благе этой ночи и последующего времени и ищу у Тебя защиты от их зла. Господь мой, защити меня от лени и тягот дряхлости. Господь мой, ищу у Тебя защиты от мучений Огня и могилы.",
        meaningEn = "We have reached the evening, and all dominion belongs to Allah. All praise is His. None is worthy of worship except Allah alone, without a partner. His are dominion and praise, and He has power over all things. My Lord, I ask You for the good in this night and what follows, and seek Your protection from their evil. My Lord, protect me from laziness and the hardship of decrepit old age. Protect me from punishment in the Fire and in the grave.",
        repetitions = 1,
        countIsPrescribed = false,
        sourceLabel = "Muslim 2723b",
        sourceUrl = "https://sunnah.com/muslim:2723b",
    )

    // Keep the exact Abu Dawud 5068 wording; do not splice endings from other narrations.
    private val morningBika = AzkarEntry(
        id = "morning_bika",
        titleRu = "С Тобой мы встречаем утро",
        titleEn = "By You we reach the morning",
        arabic = "اللَّهُمَّ بِكَ أَصْبَحْنَا وَبِكَ أَمْسَيْنَا وَبِكَ نَحْيَا وَبِكَ نَمُوتُ وَإِلَيْكَ النُّشُورُ.",
        transliterationRu = "Аллахумма бика асбахна ва бика амсайна ва бика нахья ва бика намуту ва иляйкан-нушур.",
        transliterationEn = "Allāhumma bika aṣbaḥnā wa bika amsaynā wa bika naḥyā wa bika namūtu wa ilaykan-nushūr.",
        meaningRu = "О Аллах, благодаря Тебе мы встречаем утро и вечер, благодаря Тебе живём и умираем, и к Тебе предстанем после воскрешения.",
        meaningEn = "Allah, by You we reach morning and evening, by You we live and die, and to You we shall be raised.",
        repetitions = 1,
        countIsPrescribed = false,
        sourceLabel = "Abu Dawud 5068 (sahih, al-Albani)",
        sourceUrl = "https://sunnah.com/abudawud:5068",
    )
    private val eveningBika = morningBika.copy(
        id = "evening_bika",
        titleRu = "С Тобой мы встречаем вечер",
        titleEn = "By You we reach the evening",
        arabic = "اللَّهُمَّ بِكَ أَمْسَيْنَا وَبِكَ نَحْيَا وَبِكَ نَمُوتُ وَإِلَيْكَ النُّشُورُ.",
        transliterationRu = "Аллахумма бика амсайна ва бика нахья ва бика намуту ва иляйкан-нушур.",
        transliterationEn = "Allāhumma bika amsaynā wa bika naḥyā wa bika namūtu wa ilaykan-nushūr.",
        meaningRu = "О Аллах, благодаря Тебе мы встречаем вечер, благодаря Тебе живём и умираем, и к Тебе предстанем после воскрешения.",
        meaningEn = "Allah, by You we reach the evening, by You we live and die, and to You we shall be raised.",
    )

    private val ikhlas = AzkarEntry(
        id = "ikhlas",
        titleRu = "Сура «Аль-Ихляс»",
        titleEn = "Surah Al-Ikhlas",
        arabic = "بِسْمِ اللَّهِ الرَّحْمَٰنِ الرَّحِيمِ\nقُلْ هُوَ اللَّهُ أَحَدٌ ۝١\nاللَّهُ الصَّمَدُ ۝٢\nلَمْ يَلِدْ وَلَمْ يُولَدْ ۝٣\nوَلَمْ يَكُنْ لَهُ كُفُوًا أَحَدٌ ۝٤",
        transliterationRu = "Бисмилляхир-рахманир-рахим.\nКуль хуваллаху ахад.\nАллахус-самад.\nЛям ялид ва лям юляд.\nВа лям якул-ляху куфуван ахад.",
        transliterationEn = "Bismillāhir-raḥmānir-raḥīm.\nQul huwallāhu aḥad.\nAllāhuṣ-ṣamad.\nLam yalid wa lam yūlad.\nWa lam yakul-lahu kufuwan aḥad.",
        meaningRu = "Во имя Аллаха, Милостивого, Милующего.\nСкажи: Он — Аллах, Единственный. Аллах — Тот, в Ком нуждаются все, а Он ни в ком не нуждается. Он не родил и не был рождён. И нет никого равного Ему.",
        meaningEn = "In Allah’s name, the All-Merciful, the Merciful.\nSay: He is Allah, the One. Allah is needed by all and needs none. He neither begets nor was begotten. No one is equal to Him.",
        repetitions = 3,
        sourceLabel = "Qur’an 112:1–4 · Abu Dawud 5082 (hasan)",
        sourceUrl = "https://sunnah.com/abudawud:5082",
    )
    private val falaq = AzkarEntry(
        id = "falaq",
        titleRu = "Сура «Аль-Фаляк»",
        titleEn = "Surah Al-Falaq",
        arabic = "بِسْمِ اللَّهِ الرَّحْمَٰنِ الرَّحِيمِ\nقُلْ أَعُوذُ بِرَبِّ الْفَلَقِ ۝١\nمِنْ شَرِّ مَا خَلَقَ ۝٢\nوَمِنْ شَرِّ غَاسِقٍ إِذَا وَقَبَ ۝٣\nوَمِنْ شَرِّ النَّفَّاثَاتِ فِي الْعُقَدِ ۝٤\nوَمِنْ شَرِّ حَاسِدٍ إِذَا حَسَدَ ۝٥",
        transliterationRu = "Бисмилляхир-рахманир-рахим.\nКуль а‘узу бираббиль-фаляк.\nМин шарри ма халяк.\nВа мин шарри гасикин иза вакаб.\nВа мин шаррин-наффасати филь-‘укад.\nВа мин шарри хасидин иза хасад.",
        transliterationEn = "Bismillāhir-raḥmānir-raḥīm.\nQul a‘ūdhu birabbil-falaq.\nMin sharri mā khalaq.\nWa min sharri ghāsiqin idhā waqab.\nWa min sharrin-naffāthāti fil-‘uqad.\nWa min sharri ḥāsidin idhā ḥasad.",
        meaningRu = "Во имя Аллаха, Милостивого, Милующего.\nСкажи: ищу защиты у Господа рассвета от зла того, что Он сотворил, от зла ночной тьмы, когда она наступает, от зла дующих на узлы и от зла завистника, когда он завидует.",
        meaningEn = "In Allah’s name, the All-Merciful, the Merciful.\nSay: I seek the protection of the Lord of daybreak from the evil of what He created, from the evil of darkness as it falls, from the evil of those who blow on knots, and from the evil of an envier when they envy.",
        repetitions = 3,
        sourceLabel = "Qur’an 113:1–5 · Abu Dawud 5082 (hasan)",
        sourceUrl = "https://sunnah.com/abudawud:5082",
    )
    private val nas = AzkarEntry(
        id = "nas",
        titleRu = "Сура «Ан-Нас»",
        titleEn = "Surah An-Nas",
        arabic = "بِسْمِ اللَّهِ الرَّحْمَٰنِ الرَّحِيمِ\nقُلْ أَعُوذُ بِرَبِّ النَّاسِ ۝١\nمَلِكِ النَّاسِ ۝٢\nإِلَٰهِ النَّاسِ ۝٣\nمِنْ شَرِّ الْوَسْوَاسِ الْخَنَّاسِ ۝٤\nالَّذِي يُوَسْوِسُ فِي صُدُورِ النَّاسِ ۝٥\nمِنَ الْجِنَّةِ وَالنَّاسِ ۝٦",
        transliterationRu = "Бисмилляхир-рахманир-рахим.\nКуль а‘узу бираббин-нас.\nМаликин-нас.\nИляхин-нас.\nМин шарриль-васвасиль-ханнас.\nАллязи ювасвису фи судурин-нас.\nМиналь-джиннати ван-нас.",
        transliterationEn = "Bismillāhir-raḥmānir-raḥīm.\nQul a‘ūdhu birabbin-nās.\nMalikin-nās.\nIlāhin-nās.\nMin sharril-waswāsil-khannās.\nAlladhī yuwaswisu fī ṣudūrin-nās.\nMinal-jinnati wan-nās.",
        meaningRu = "Во имя Аллаха, Милостивого, Милующего.\nСкажи: ищу защиты у Господа людей, Царя людей, Бога людей от зла наущающего, который отступает и скрывается, который нашёптывает в груди людей, — из числа джиннов и людей.",
        meaningEn = "In Allah’s name, the All-Merciful, the Merciful.\nSay: I seek the protection of the Lord of people, the King of people, the God of people, from the evil of the retreating whisperer who whispers into people’s hearts, from among jinn and people.",
        repetitions = 3,
        sourceLabel = "Qur’an 114:1–6 · Abu Dawud 5082 (hasan)",
        sourceUrl = "https://sunnah.com/abudawud:5082",
    )
    private val istighfar = AzkarEntry(
        id = "sayyid_istighfar",
        titleRu = "Саййид аль-истигфар",
        titleEn = "Sayyid al-Istighfar",
        arabic = "اللَّهُمَّ أَنْتَ رَبِّي لَا إِلَهَ إِلَّا أَنْتَ، خَلَقْتَنِي وَأَنَا عَبْدُكَ، وَأَنَا عَلَى عَهْدِكَ وَوَعْدِكَ مَا اسْتَطَعْتُ، أَعُوذُ بِكَ مِنْ شَرِّ مَا صَنَعْتُ، أَبُوءُ لَكَ بِنِعْمَتِكَ عَلَيَّ وَأَبُوءُ لَكَ بِذَنْبِي، فَاغْفِرْ لِي فَإِنَّهُ لَا يَغْفِرُ الذُّنُوبَ إِلَّا أَنْتَ.",
        transliterationRu = "Аллахумма анта рабби, ля иляха илля ант. Халяктани ва ана ‘абдук, ва ана ‘аля ‘ахдика ва ва‘дика мастата‘т. А‘узу бика мин шарри ма сана‘т. Абу’у ляка бини‘матика ‘аляйя ва абу’у ляка бизанби. Фагфир ли, фа-иннаху ля ягфируз-зунуба илля ант.",
        transliterationEn = "Allāhumma anta rabbī, lā ilāha illā ant. Khalaqtanī wa anā ‘abduk, wa anā ‘alā ‘ahdika wa wa‘dika mastaṭa‘t. A‘ūdhu bika min sharri mā ṣana‘t. Abū’u laka bini‘matika ‘alayya wa abū’u laka bidhanbī. Faghfir lī, fa-innahu lā yaghfirudh-dhunūba illā ant.",
        meaningRu = "О Аллах, Ты мой Господь, и нет божества, достойного поклонения, кроме Тебя. Ты создал меня, и я Твой раб. По мере сил я верен завету и обещанию Тебе. Ищу у Тебя защиты от зла, которое совершил. Признаю Твои милости ко мне и признаю свой грех. Прости меня: никто, кроме Тебя, не прощает грехи.",
        meaningEn = "Allah, You are my Lord; none is worthy of worship except You. You created me, and I am Your servant. I keep my covenant and promise to You as far as I can. I seek Your protection from the evil I have done. I acknowledge Your gifts to me and admit my sin. Forgive me, for no one but You forgives sins.",
        repetitions = 1,
        countIsPrescribed = false,
        sourceLabel = "Al-Bukhari 6306",
        sourceUrl = "https://sunnah.com/bukhari:6306",
    )
    private val protection = AzkarEntry(
        id = "bismillah_protection",
        titleRu = "С именем Аллаха",
        titleEn = "In the name of Allah",
        arabic = "بِسْمِ اللَّهِ الَّذِي لَا يَضُرُّ مَعَ اسْمِهِ شَيْءٌ فِي الْأَرْضِ وَلَا فِي السَّمَاءِ وَهُوَ السَّمِيعُ الْعَلِيمُ.",
        transliterationRu = "Бисмилляхиллязи ля ядурру ма‘асмихи шай’ун филь-арди ва ля фис-сама’и ва хувас-сами‘уль-‘алим.",
        transliterationEn = "Bismillāhilladhī lā yaḍurru ma‘asmihi shay’un fil-arḍi wa lā fis-samā’i wa huwas-samī‘ul-‘alīm.",
        meaningRu = "С именем Аллаха, с Чьим именем ничто на земле и на небе не причинит вреда. Он — Слышащий, Знающий.",
        meaningEn = "In the name of Allah, with whose name nothing on earth or in heaven can cause harm. He hears all and knows all.",
        repetitions = 3,
        sourceLabel = "Abu Dawud 5088 (sahih, al-Albani)",
        sourceUrl = "https://sunnah.com/abudawud:5088",
    )
    private val praise = AzkarEntry(
        id = "subhanallah_100",
        titleRu = "Прославление и хвала",
        titleEn = "Glorification and praise",
        arabic = "سُبْحَانَ اللَّهِ وَبِحَمْدِهِ",
        transliterationRu = "Субханаллахи ва бихамдих.",
        transliterationEn = "Subḥānallāhi wa biḥamdih.",
        meaningRu = "Аллах пречист от любого недостатка, и Ему хвала.",
        meaningEn = "Allah is free from every imperfection, and all praise is His.",
        repetitions = 100,
        sourceLabel = "Muslim 2692",
        sourceUrl = "https://sunnah.com/muslim:2692",
    )
    private val morningPraise = AzkarEntry(
        id = "morning_creation_praise",
        titleRu = "Утреннее прославление",
        titleEn = "Morning glorification",
        arabic = "سُبْحَانَ اللَّهِ وَبِحَمْدِهِ عَدَدَ خَلْقِهِ وَرِضَا نَفْسِهِ وَزِنَةَ عَرْشِهِ وَمِدَادَ كَلِمَاتِهِ.",
        transliterationRu = "Субханаллахи ва бихамдихи ‘адада халькихи ва рида нафсихи ва зината ‘аршихи ва мидада калиматих.",
        transliterationEn = "Subḥānallāhi wa biḥamdihi ‘adada khalqihi wa riḍā nafsihi wa zinata ‘arshihi wa midāda kalimātih.",
        meaningRu = "Пречист Аллах, и хвала Ему — по числу Его творений, настолько, насколько Он доволен, по весу Его Трона и по количеству чернил для записи Его слов.",
        meaningEn = "Allah is free from imperfection, and praise is His: as numerous as His creation, as great as His pleasure, as weighty as His Throne, and as vast as the ink for His words.",
        repetitions = 3,
        sourceLabel = "Muslim 2726a",
        sourceUrl = "https://sunnah.com/muslim:2726a",
    )
    private val eveningRefuge = AzkarEntry(
        id = "evening_perfect_words",
        titleRu = "Защита совершенными словами Аллаха",
        titleEn = "Seeking refuge in Allah’s perfect words",
        arabic = "أَعُوذُ بِكَلِمَاتِ اللَّهِ التَّامَّاتِ مِنْ شَرِّ مَا خَلَقَ.",
        transliterationRu = "А‘узу бикалиматилляхит-таммати мин шарри ма халяк.",
        transliterationEn = "A‘ūdhu bikalimātillāhit-tāmmāti min sharri mā khalaq.",
        meaningRu = "Ищу защиты в совершенных словах Аллаха от зла того, что Он сотворил.",
        meaningEn = "I seek protection in Allah’s perfect words from the evil of what He created.",
        repetitions = 3,
        sourceLabel = "At-Tirmidhi 3604b (sahih, Darussalam)",
        sourceUrl = "https://sunnah.com/tirmidhi:3604b",
    )

    private val shared = listOf(ikhlas, falaq, nas, istighfar, protection, praise)
    private val morning = listOf(morningOpening, morningBika) + shared + morningPraise
    private val evening = listOf(eveningOpening, eveningBika) + shared + eveningRefuge

    /** Unknown/deep-link values use the safe default morning collection. */
    fun items(period: String): List<AzkarEntry> = if (period == "evening") evening else morning
}
