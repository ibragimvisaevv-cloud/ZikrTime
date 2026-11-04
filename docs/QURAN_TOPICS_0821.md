# Тематический указатель Корана — 0.8.21

## Назначение и границы

16 тем, 64 отрывка, 143 ссылки на аяты (139 разных аятов). Это редакционный указатель для чтения, а не полный тафсир, фетва или перечень единственно допустимых толкований. Названия и короткие описания тем — текст интерфейса приложения. Независимая проверка богословом и носителями всех восьми языков не заявляется.

Арабский текст, огласовки, перевод, примечания и контрольные суммы в `assets/quran/` не менялись. На карточке отрывка показано начало исходного русского перевода с явной подписью языка и источника. Открывается вся сура с первого указанного аята; соседние аяты доступны. Интерфейс переведён на восемь языков, но новые переводы Корана не создавались.

## Навигация

В каталоге Корана есть вкладка «Темы». Поиск принимает название/описание темы, название суры или ссылку на аят (`2:155` найдёт диапазон `2:153–157`). Регистр, лишние пробелы и диакритика поисковой строки не мешают поиску; исходный текст не нормализуется. Пустой результат имеет пояснение и очистку.

Тема, запрос, положение каталога и положение отрывков хранятся раздельно на уровне `QuranScreen`. Переход в читалку и назад сохраняет подборку; стрелка и системный Back сначала возвращают из чтения, затем из подборки. Новый выбор темы начинает её с начала. В читалке остаются прежние закладки, заметки и оценка чтения; переход к аяту не заполняет пропуски статистики.

Одна LazyColumn на экране, FlowRow для вкладок, карточки без фиксированной высоты и переносимые заголовки предназначены для узких экранов и увеличенного шрифта. Итог выполнения UI и сборки фиксируется в основной заметке релиза после CI.

## Полный список и первоисточники

Все выбранные русские аяты прочитаны и сверены с закреплёнными ответами QuranEnc / Rowwad из исходного коммита `c592403a83b5455719b35dc1570399406e676efa`. Проверена тематическая связь и сохранён ближайший необходимый контекст. В частности, включены продолжения 7:157, 76:10, 25:66; 13:27–28 читаются вместе, а 65:1–3 сохраняют контекст развода. Для 3:133–136, 4:36–38 и 3:190–194 сохранено окончание связной фразы/мольбы. 31:14–15 вместе сохраняют оговорку о послушании родителям.

| Тема | Отрывки (ссылки на QuranEnc / Rowwad) |
|---|---|
| Терпение | [2:153–157](https://quranenc.com/en/browse/russian_rwwad/2/153); [3:200](https://quranenc.com/en/browse/russian_rwwad/3/200); [16:126–128](https://quranenc.com/en/browse/russian_rwwad/16/126); [103:1–3](https://quranenc.com/en/browse/russian_rwwad/103/1) |
| Благодарность | [2:152](https://quranenc.com/en/browse/russian_rwwad/2/152); [14:7–8](https://quranenc.com/en/browse/russian_rwwad/14/7); [16:78](https://quranenc.com/en/browse/russian_rwwad/16/78); [31:12](https://quranenc.com/en/browse/russian_rwwad/31/12) |
| Родители | [17:23–24](https://quranenc.com/en/browse/russian_rwwad/17/23); [31:14–15](https://quranenc.com/en/browse/russian_rwwad/31/14); [46:15](https://quranenc.com/en/browse/russian_rwwad/46/15); [29:8](https://quranenc.com/en/browse/russian_rwwad/29/8) |
| Милосердие | [7:156–157](https://quranenc.com/en/browse/russian_rwwad/7/156); [21:107](https://quranenc.com/en/browse/russian_rwwad/21/107); [39:53–54](https://quranenc.com/en/browse/russian_rwwad/39/53); [90:12–17](https://quranenc.com/en/browse/russian_rwwad/90/12) |
| Покаяние | [4:17–18](https://quranenc.com/en/browse/russian_rwwad/4/17); [25:68–71](https://quranenc.com/en/browse/russian_rwwad/25/68); [39:53–54](https://quranenc.com/en/browse/russian_rwwad/39/53); [66:8](https://quranenc.com/en/browse/russian_rwwad/66/8) |
| Честность | [9:119](https://quranenc.com/en/browse/russian_rwwad/9/119); [33:70–71](https://quranenc.com/en/browse/russian_rwwad/33/70); [22:30–31](https://quranenc.com/en/browse/russian_rwwad/22/30); [83:1–6](https://quranenc.com/en/browse/russian_rwwad/83/1) |
| Справедливость | [4:58](https://quranenc.com/en/browse/russian_rwwad/4/58); [4:135](https://quranenc.com/en/browse/russian_rwwad/4/135); [5:8](https://quranenc.com/en/browse/russian_rwwad/5/8); [16:90](https://quranenc.com/en/browse/russian_rwwad/16/90) |
| Благотворительность | [2:261–264](https://quranenc.com/en/browse/russian_rwwad/2/261); [2:267](https://quranenc.com/en/browse/russian_rwwad/2/267); [2:271](https://quranenc.com/en/browse/russian_rwwad/2/271); [76:8–10](https://quranenc.com/en/browse/russian_rwwad/76/8) |
| Знание | [20:114](https://quranenc.com/en/browse/russian_rwwad/20/114); [39:9](https://quranenc.com/en/browse/russian_rwwad/39/9); [58:11](https://quranenc.com/en/browse/russian_rwwad/58/11); [96:1–5](https://quranenc.com/en/browse/russian_rwwad/96/1) |
| Поминание Аллаха | [13:27–28](https://quranenc.com/en/browse/russian_rwwad/13/27); [33:41–42](https://quranenc.com/en/browse/russian_rwwad/33/41); [2:152](https://quranenc.com/en/browse/russian_rwwad/2/152); [7:205–206](https://quranenc.com/en/browse/russian_rwwad/7/205) |
| Упование на Аллаха | [3:159–160](https://quranenc.com/en/browse/russian_rwwad/3/159); [14:11–12](https://quranenc.com/en/browse/russian_rwwad/14/11); [65:1–3](https://quranenc.com/en/browse/russian_rwwad/65/1); [11:123](https://quranenc.com/en/browse/russian_rwwad/11/123) |
| Дуа | [2:186](https://quranenc.com/en/browse/russian_rwwad/2/186); [2:201](https://quranenc.com/en/browse/russian_rwwad/2/201); [3:8–9](https://quranenc.com/en/browse/russian_rwwad/3/8); [14:40–41](https://quranenc.com/en/browse/russian_rwwad/14/40) |
| Прощение людей | [3:133–136](https://quranenc.com/en/browse/russian_rwwad/3/133); [24:22](https://quranenc.com/en/browse/russian_rwwad/24/22); [41:34–35](https://quranenc.com/en/browse/russian_rwwad/41/34); [42:40–43](https://quranenc.com/en/browse/russian_rwwad/42/40) |
| Скромность | [25:63–66](https://quranenc.com/en/browse/russian_rwwad/25/63); [31:18–19](https://quranenc.com/en/browse/russian_rwwad/31/18); [17:37–38](https://quranenc.com/en/browse/russian_rwwad/17/37); [49:11–12](https://quranenc.com/en/browse/russian_rwwad/49/11) |
| Забота о ближних | [4:36–38](https://quranenc.com/en/browse/russian_rwwad/4/36); [17:26–29](https://quranenc.com/en/browse/russian_rwwad/17/26); [30:21](https://quranenc.com/en/browse/russian_rwwad/30/21); [49:10](https://quranenc.com/en/browse/russian_rwwad/49/10) |
| Размышление | [3:190–194](https://quranenc.com/en/browse/russian_rwwad/3/190); [30:20–25](https://quranenc.com/en/browse/russian_rwwad/30/20); [67:3–4](https://quranenc.com/en/browse/russian_rwwad/67/3); [88:17–20](https://quranenc.com/en/browse/russian_rwwad/88/17) |

## Дополнительная онлайн-сверка

04.11.2026 просмотрены доступные страницы/индексированные фрагменты официального портала Diyanet и QuranEnc по следующим ссылкам. Это дополнительная выборочная сверка, а не утверждение о посещении всех 64 страниц. Динамические страницы Diyanet при прямом открытии иногда отдают лишь оболочку; текст ряда страниц доступен в результатах поиска. QuranEnc 2:153 при прямом открытии вернул ошибку доступа, 7:156 был доступен с русским текстом. Полная содержательная сверка всех выбранных аятов опирается на закреплённые файлы, перечисленные ниже.

- [2:153](https://kuran.diyanet.gov.tr/tefsir/Bakara-suresi/160/153-ayet-tefsiri)
- [14:7–8](https://kuran.diyanet.gov.tr/tefsir/%C4%B0br%C3%A2h%C3%AEm-suresi/1757/7-8-ayet-tefsiri)
- [17:23–24](https://kuran.diyanet.gov.tr/tefsir/%C4%B0sr%C3%A2-suresi/2052/23-24-ayet-tefsiri)
- [4:17–18](https://kuran.diyanet.gov.tr/tefsir/Nis%C3%A2-suresi/510/17-18-ayet-tefsiri)
- [9:119](https://kuran.diyanet.gov.tr/tefsir/Tevbe-suresi/1354/119-ayet-tefsiri)
- [5:8](https://kuran.diyanet.gov.tr/tefsir/M%C3%83%C2%A2ide-suresi/677/8-ayet-tefsiri)
- [20:113–114](https://kuran.diyanet.gov.tr/tefsir/T%C3%A2h%C3%A2-suresi/2461/113-114-ayet-tefsiri)
- [13:28](https://www.diyanet.gov.tr/icerik/47809)
- [2:186](https://kuran.diyanet.gov.tr/tefsir/Bakara-suresi/193/186-ayet-tefsiri)
- [24:22](https://kuran.diyanet.gov.tr/tefsir/N%C3%BBr-suresi/2813/22-ayet-tefsiri)
- [25:63–66](https://kuran.diyanet.gov.tr/tefsir/Furk%C3%A2n-suresi/2918/63-66-ayet-tefsiri)
- [30:21](https://kuran.diyanet.gov.tr/tefsir/R%C3%BBm-suresi/3430/21-ayet-tefsiri)
- [3:190–194](https://kuran.diyanet.gov.tr/mushaf/kuran-meal-2/al-i-imran-suresi-3/ayet-190/diyanet-isleri-baskanligi-meali-1)
- [7:156](https://quranenc.com/en/browse/russian_rwwad/7/156)

## Зафиксированные файлы источника

Манифест Корана SHA-256: `0ad3cd6003fda2bcc8ea3dbde94925e67a1594c2b58c01ab62df98dfa17f6b84`.
Указанные ниже SHA-256 взяты из неизменённого манифеста; загрузчик проверяет эти байты перед созданием QuranBook.

| Файл под assets/quran/ | SHA-256 |
|---|---|
| sources/russian_rwwad/2.json | `68113c003f8d8b3ea6856c9bcd763621b851816bda6e2f5a5c8da29dcff6f3d2` |
| sources/russian_rwwad/3.json | `1edb35816df03870f61025100c124e39296f5d0b478922c1afccd1582dd10947` |
| sources/russian_rwwad/4.json | `6a70823596459ef4ccae801b3b67d5be03bf0a76296c323a0794cabe6d591729` |
| sources/russian_rwwad/5.json | `5b6cf4671e8dc5cd79f57db3c16b2ab901aec1069113afae070825229f520cd2` |
| sources/russian_rwwad/7.json | `f27da702f99cd771d37ae66e75d1f334b5ff56590b2f5a4aff35e05e179063c9` |
| sources/russian_rwwad/9.json | `e91e27a7a51d76f3da2f9edcbf28a9620d18e9b13b8938c6b7384c5f5c498059` |
| sources/russian_rwwad/11.json | `3cdd0f9d255a0d5cd33e1341ab83662fca36d6a9df0a433c1b4b2f242c444d82` |
| sources/russian_rwwad/13.json | `fbdebe8ec62bd5638a9edc0d2149ddb552149c75d82a11f2508f9e2b17a9db51` |
| sources/russian_rwwad/14.json | `7b1e3fdb309fe1540076a6e445014cd8538cffa49e34d93ff92fe86ccfbfda71` |
| sources/russian_rwwad/16.json | `02d21026c3a17b46ab6f7553ac66e4388b7e2882240dfdf307ce0700c2ad543f` |
| sources/russian_rwwad/17.json | `774250d856cb9b0007b750d21bf500a5dc9f89b1728011d74b7b3031e5acaacb` |
| sources/russian_rwwad/20.json | `4b4844731b202cf57d4ef283f0e4c4756472eaa12b51363205b241700f76cea9` |
| sources/russian_rwwad/21.json | `c8f54dc8f07c833c7a3c0c12b44350c48b7e200bce19da419b32979dbdc316f4` |
| sources/russian_rwwad/22.json | `88f0d9e3fc1488f2d5c50679227ba8306a99651dc45217ac2f4f0d8e90d785c1` |
| sources/russian_rwwad/24.json | `653d035ae65581a2ad40ce6701a125cd5b4166020e83be89e763d3fb79dd77f8` |
| sources/russian_rwwad/25.json | `b379099c723355b55b763ad69d4b7aa4c3735b387c996ccc6b6e17f34775c3fb` |
| sources/russian_rwwad/29.json | `26a88f258b0303718e379052b4c3082ef1599a5441c6d4525e647deeed0a0260` |
| sources/russian_rwwad/30.json | `909f3b82b1f176c6599cc6e1d9662deebfd538bb29ab12997c60e72753c8a719` |
| sources/russian_rwwad/31.json | `6cda69a561ec947f6b44816080a201d01f3bba813aec4d2e3b8cd0a4e03d5426` |
| sources/russian_rwwad/33.json | `abb45cd6adc68adff57925c06c398640fbdf7ff6b675d6541a82f211e97257bd` |
| sources/russian_rwwad/39.json | `9c5806c991d021a8727a0317878bd289ca6723684dda815e246151272996286e` |
| sources/russian_rwwad/41.json | `853ddb7e984e159e74a805b57f51012f622dcd408913abcd9907df93ba22c53c` |
| sources/russian_rwwad/42.json | `3ace9a09e62f4aab38f640fc6b976bf87c817bf0633ffbef73415e884519bc57` |
| sources/russian_rwwad/46.json | `be44ed33558b8bd2415cef7ade75ae392773a79a8c3c6eb93d236f20830d55a9` |
| sources/russian_rwwad/49.json | `4bca4fdccd05f79c514caa62e0848da9b0396fd30792d8bb33d9fbebec4ba8bc` |
| sources/russian_rwwad/58.json | `50a3be0c6e21916fa0e7651a9dfe285169ce11c871983e75fe5ce8d5b9faba15` |
| sources/russian_rwwad/65.json | `14008fa159630ff8ad33e225cf4becae3c47e893fb63d26f923c1a42a8395546` |
| sources/russian_rwwad/66.json | `aecebd05ff6cca6bc0b60020eb2d32ff58af03492e0c4f8b59e7fc980584ec0f` |
| sources/russian_rwwad/67.json | `355f5c966fcee891c4ce4349f54118078806032fa05f2c683715fefa7899689d` |
| sources/russian_rwwad/76.json | `ea0aefa1d4d41fa080bf7fd2c9dde5a12641ccc7f8ad3f2b68d323e93aa19149` |
| sources/russian_rwwad/83.json | `11aae1eb295d882cd0f8f392b4bd2bc6b46ffeade9b80b948a6cc12587098d08` |
| sources/russian_rwwad/88.json | `fefa09b5e16496e0ff4ce4d9f86fe8c2493c4456462477f0b48f5e6dcd665314` |
| sources/russian_rwwad/90.json | `71ac534d3a67265c3cddf322406584a8e8353613c6041de60575a78b5f4beadb` |
| sources/russian_rwwad/96.json | `b2e510b8c1c044f0237ea1445fdb9d168ebd28a3740e9d2a3987b6e66a556f8b` |
| sources/russian_rwwad/103.json | `9bbfff61ebf53c29e68b787d6a8dfedb9cddfc170e28f50ffc697984e10ccefa` |

## Проверки

`QuranTopicsTest`: реальные диапазоны/неизменённые объекты текста, поиск внутри диапазона, неизвестные ссылки и пустой запрос, регистр/диакритика локализованных названий, точный якорь первого аята в режиме чтения, отказ при пропущенном аяте.

UI-контракт: `quran_topics`, `quran_topics_search`, `quran_topics_search_clear`, `quran_topics_empty`, `quran_topic_patience`, `quran_topic_detail_patience`, `quran_topics_back`, `quran_topic_passage_patience_2_153_157`, `quran_topic_source_patience_2_153_157`; списки `quran_topics_list` и `quran_topics_detail_list`. Прежний тег возврата читалки `quran_surahs` сохранён.

Локальная сборка после отключения хоста не запускалась. Тесты/визуальные сценарии должны выполняться в облачном CI; успешность здесь заранее не объявляется.
