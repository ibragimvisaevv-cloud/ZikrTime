# Native home-screen widgets; all supported application languages.
LANGS=['en','ru','uz','tg','kk','ky','az','tr']
ROWS={
'widget_compact_name':['Next prayer · 2×1','Ближайший намаз · 2×1','Keyingi namoz · 2×1','Намози навбатӣ · 2×1','Келесі намаз · 2×1','Кийинки намаз · 2×1','Növbəti namaz · 2×1','Sonraki namaz · 2×1'],
'widget_timeline_name':['Timeline · 4×1','Линия времени · 4×1','Vaqt chizig‘i · 4×1','Хатти вақт · 4×1','Уақыт сызығы · 4×1','Убакыт тилкеси · 4×1','Vaxt xətti · 4×1','Zaman çizgisi · 4×1'],
'widget_square_name':['My prayer · 2×2','Мой намаз · 2×2','Mening namozim · 2×2','Намози ман · 2×2','Менің намазым · 2×2','Менин намазым · 2×2','Namazım · 2×2','Namazım · 2×2'],
'widget_day_name':['Day schedule · 4×2','Расписание дня · 4×2','Kun jadvali · 4×2','Ҷадвали рӯз · 4×2','Күн кестесі · 4×2','Күндүн жадыбалы · 4×2','Gün cədvəli · 4×2','Günlük vakitler · 4×2'],
'widget_countdown_label':['Starts in','До начала','Boshlanishiga','То оғоз','Басталуына','Башталышына','Başlanmasına','Kalan süre'],
'widget_setup_city':['Choose your city →','Выбрать город →','Shaharni tanlash →','Интихоби шаҳр →','Қаланы таңдау →','Шаарды тандоо →','Şəhəri seçin →','Şehir seçin →'],
'widget_refresh':['Refresh ↻','Обновить ↻','Yangilash ↻','Навсозӣ ↻','Жаңарту ↻','Жаңыртуу ↻','Yenilə ↻','Yenile ↻'],
'widget_config_title':['Your horizon','Ваш горизонт','Sizning ufqingiz','Уфуқи шумо','Сіздің көкжиегіңіз','Сиздин горизонтуңуз','Üfüqünüz','Ufkunuz'],
'widget_config_hint':['Widget appearance. Your city and prayer settings come from ZikrTime.','Оформление виджета. Город и параметры намаза берутся из ZikrTime.','Vidjet ko‘rinishi. Shahar va namoz sozlamalari ZikrTime’dan olinadi.','Намуди виҷет. Шаҳр ва танзимоти намоз аз ZikrTime гирифта мешаванд.','Виджет көрінісі. Қала мен намаз параметрлері ZikrTime-нан алынады.','Виджеттин көрүнүшү. Шаар жана намаз жөндөөлөрү ZikrTime аркылуу алынат.','Vidcetin görünüşü. Şəhər və namaz ayarları ZikrTime-dan alınır.','Widget görünümü. Şehir ve namaz ayarları ZikrTime’dan alınır.'],
'widget_theme_app':['Match the app','Как в приложении','Ilovaga mos','Мисли барнома','Қолданбадағыдай','Колдонмодогудай','Tətbiqə uyğun','Uygulamayla aynı'],
'widget_hijri_option':['Hijri date when space allows','Дата по хиджре, если хватает места','Joy yetarli bo‘lsa hijriy sana','Санаи ҳиҷрӣ, агар ҷой бошад','Орын болса хижра күні','Орун болсо хижра күнү','Yer olduqda hicri tarix','Yer varsa hicri tarih'],
}
STRINGS={lang:{key:values[i] for key,values in ROWS.items()} for i,lang in enumerate(LANGS)}
