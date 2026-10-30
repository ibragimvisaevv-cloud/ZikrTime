"""Controls for the sourced morning/evening collection; Arabic and meanings live in AzkarContent."""
LANGS=['ru','en','uz','tg','kk','ky','az','tr']
ROWS={
'azkar_source_button':['Источник','Source','Manba','Манбаъ','Дереккөз','Булак','Mənbə','Kaynak'],
'azkar_repetitions':['Повторения: %1$d / %2$d','Repetitions: %1$d / %2$d','Takror: %1$d / %2$d','Такрор: %1$d / %2$d','Қайталау: %1$d / %2$d','Кайталоо: %1$d / %2$d','Təkrar: %1$d / %2$d','Tekrar: %1$d / %2$d'],
'azkar_mark_read':['Отметить прочитанным','Mark as read','O‘qilgan deb belgilash','Хондашуда қайд кардан','Оқылды деп белгілеу','Окулду деп белгилөө','Oxundu kimi qeyd et','Okundu olarak işaretle'],
'azkar_read_done':['Прочитано','Read','O‘qildi','Хонда шуд','Оқылды','Окулду','Oxundu','Okundu'],
'azkar_sources_note':[
'Избранная подборка утренних и вечерних азкаров, а не полный перечень. Некоторые тексты читаются в оба времени. Формулировка и число повторений связаны с указанным сообщением.',
'A selected morning and evening collection, not an exhaustive list. Some texts are read in both periods. The wording and repetitions are linked to the cited narration.',
'Tonggi va kechki azkorlarning tanlangan to‘plami, to‘liq ro‘yxat emas. Ayrim matnlar ikkala vaqtda ham o‘qiladi. Matn va takrorlar ko‘rsatilgan rivoyatga asoslangan.',
'Маҷмӯаи интихобшудаи азкори субҳу шом, на рӯйхати пурра. Баъзе матнҳо дар ҳар ду вақт хонда мешаванд. Матн ва такрорҳо ба ривояти зикршуда асос меёбанд.',
'Таңғы және кешкі зікірлердің таңдаулы жинағы, толық тізім емес. Кейбір мәтіндер екі уақытта да оқылады. Мәтін мен қайталау саны көрсетілген риуаятқа негізделген.',
'Эртең мененки жана кечки зикирлердин тандалган жыйнагы, толук тизме эмес. Айрым тексттер эки убакта тең окулат. Текст жана кайталоолор көрсөтүлгөн риваятка негизделген.',
'Səhər və axşam zikrlərindən seçmə toplusu, tam siyahı deyil. Bəzi mətnlər hər iki vaxtda oxunur. Mətn və təkrar sayı göstərilən rəvayətə əsaslanır.',
'Sabah ve akşam zikirlerinden seçilmiş bir derleme; kapsamlı bir liste değildir. Bazı metinler her iki vakitte okunur. Metin ve tekrar sayısı belirtilen rivayete dayanır.'],
'azkar_single_read_note':[
'Кнопка отмечает одно чтение. Отдельное число повторений для этого текста здесь не устанавливается.',
'The button records one reading; it does not prescribe an additional repetition count.',
'Tugma bir o‘qishni qayd etadi; alohida takror sonini belgilamaydi.',
'Тугма як хонданро қайд мекунад; шумораи иловагии такрорро муқаррар намекунад.',
'Түйме бір оқуды белгілейді; қосымша қайталау санын белгілемейді.',
'Баскыч бир окууну белгилейт; кошумча кайталоо санын белгилебейт.',
'Düymə bir oxunuşu qeyd edir; əlavə təkrar sayı təyin etmir.',
'Düğme bir okumayı kaydeder; ek bir tekrar sayısı belirlemez.'],
'azkar_translation_note':[
'Русский и английский тексты — смысловой перевод для этой подборки. Транскрипция помогает чтению, но не заменяет арабское произношение.',
'Russian and English are meaning-based translations prepared for this collection. Transliteration is a reading aid, not a replacement for Arabic pronunciation.',
'Ruscha va inglizcha matnlar ushbu to‘plam uchun ma’no tarjimasidir. Transkripsiya arabcha talaffuz o‘rnini bosmaydi.',
'Матнҳои русӣ ва англисӣ тарҷумаи маъно барои ин маҷмӯаанд. Транскрипсия ҷойгузини талаффузи арабӣ нест.',
'Орысша және ағылшынша мәтіндер — осы жинаққа арналған мағыналық аударма. Транскрипция арабша айтылымды алмастырмайды.',
'Орусча жана англисче тексттер — бул жыйнак үчүн маанилик котормо. Транскрипция арабча айтылышты алмаштырбайт.',
'Rus və ingilis mətnləri bu toplu üçün məna tərcüməsidir. Transkripsiya ərəb tələffüzünü əvəz etmir.',
'Rusça ve İngilizce metinler bu derleme için hazırlanmış anlam çevirileridir. Transkripsiyon Arapça telaffuzun yerini tutmaz.'],
}
STRINGS={lang:{key:values[i] for key,values in ROWS.items()} for i,lang in enumerate(LANGS)}
