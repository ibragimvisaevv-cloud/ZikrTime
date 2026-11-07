# ZikrTime 0.8.21: источники новых вопросов среднего уровня

Подготовлено 110 новых вопросов с ID `l2x_*`. Вместе с 40 вопросами 0.8.20 средний уровень содержит 150 вопросов. Старые ID, тексты и версии смысла не переписываются.

Вопросы проверяют связь наставлений, условия, противопоставления, последовательность действий и конкретные перечни. Варианты одного вопроса должны быть сопоставимы по смыслу и длине; одобряемое действие из другого аята может быть неверным ответом на вопрос о конкретном месте. Формулировки вопросов и объяснений являются авторским учебным пересказом, а не цитатами или официальным переводом Корана.

## Основа содержательной проверки

Каждый правильный ответ и объяснение сверены с точным текстом закреплённого русского перевода QuranEnc / Rowwad в `app/src/main/assets/quran/sources/russian_rwwad` исходного коммита `c592403a83b5455719b35dc1570399406e676efa`. Прочитаны все указанные ниже аяты; наличие ссылки проверено отдельно. Всего 114 различных ссылок на аяты из 31 суры, включая 24:58 как контекст перехода к взрослому правилу в 24:59. Арабский текст, исходные переводы и файлы источников не изменялись.

Авторские материалы распределены по `tools/quiz/levels/intermediate_batch*_0821.json`. Черновики `tools/quiz/drafts` не включаются в приложение. Все восемь локалей используют одинаковые ID, источники и порядок смысла вариантов; исходный правильный вариант — индекс0, экран перемешивает варианты. Проверка схемы, количества и ссылок не заменяет содержательную проверку.

Независимая проверка богословом и носителями всех восьми языков не проводилась и не заявляется. Классификация сложности — редакционная; она ещё не калибрована по результатам большой группы пользователей. Поля `originalReview` и `translationReview` не выдумываются. Банк не выдаётся за исчерпывающий курс фикха или тафсира; различия правовых школ не сводятся к одному неоговорённому ответу.

## Выборочная онлайн-сверка 4 ноября 2026 года

Официальный портал Diyanet использован для дополнительного чтения конкретных мест:

- [2:267, качество пожертвования](https://kuran.diyanet.gov.tr/tefsir/Bakara-suresi/274/267-ayet-tefsiri): соответствие вопроса качеству дара, а не только сумме.
- [2:267–268](https://kuran.diyanet.gov.tr/kuran?ayet=265&mode=tefsir&sure=2): противопоставление угрозы бедности и обещания прощения/щедрости.
- [17:36](https://kuran.diyanet.gov.tr/tefsir/%C4%B0sr%C3%A2-suresi/2065/36-ayet-tefsiri): слух, зрение, сердце и ответственность за следование без знания.
- [31:14–15](https://kuran.diyanet.gov.tr/mushaf/kuran-tefsir-1/lokman-suresi-31/ayet-19/kuran-yolu-meali-5): отказ повиноваться в ширке при сохранении доброго отношения к родителям.
- [42:40 и ближайший контекст](https://kuran.diyanet.gov.tr/kuran?ayet=32&mode=kuran-meal&sure=42): равноценность ответа и отдельное указание на прощение с исправлением.

Прямое открытие страниц QuranEnc сур16 и24 возвращало HTTP429. Это не считается успешной онлайн-сверкой; для полного покрытия использованы закреплённые исходные файлы. Онлайн-чтение перечисленных мест не обозначает онлайн-проверку всех114 ссылок.

## Контроль повторов и трактовок

Проверены ранее опубликованные203 вопроса и согласованы области новых beginner/advanced. Пересечение одной суры или нравственной темы само по себе допустимо: новый вопрос должен проверять конкретную другую связь или контекст, а не только перефразировать прежний. У новых110 нет повторяющихся ID или одинаковых формулировок вопросов. Одинаковый нравственный термин в разных контекстах не заявляется отдельной темой знаний.

В 24:58–59 сохранено различие между тремя часами уединения для детей и общим правилом после зрелости. В 31:15 отказ от конкретного недозволенного требования не превращён в требование разрыва с родителями. В 41:34 примиряющий лучший ответ не представлен гарантией управления другим человеком. В 42:40 прощение не смешано с одобрением несправедливости.

## Категории и смысловое разграничение

43 новых вопроса относятся к существующей категории «Основы и фикх» (`fiqh`): практическая нравственность, имущество, помощь, клятвы, речь, вход в дом и семейные отношения. Остальные67 новых вопросов остаются в `quran`: конкретные текстовые связи, образы, перечни и контекст наставлений. Весь средний уровень150 распределяется так: Коран91, Основы и фикх50, Сира5, Пророки4. Категории сиры и пророков не наполняются искусственно нравственными вопросами.

Карта новых `fiqh` ID:

- `l2x_truth_not_mixed`
- `l2x_advice_and_self`
- `l2x_covenant_speech`
- `l2x_food_and_steps`
- `l2x_unjust_gain_judges`
- `l2x_spending_and_ruin`
- `l2x_best_provision`
- `l2x_quality_of_giving`
- `l2x_hidden_charity`
- `l2x_restrained_needy`
- `l2x_covenant_and_piety`
- `l2x_community_call`
- `l2x_sin_response`
- `l2x_not_covet_shares`
- `l2x_conceal_bounty`
- `l2x_giving_for_show`
- `l2x_intercession_share`
- `l2x_defending_treachery`
- `l2x_wrong_then_forgiveness`
- `l2x_blame_innocent`
- `l2x_good_private_talk`
- `l2x_reconciliation_good`
- `l2x_wronged_speech_exception`
- `l2x_public_hidden_pardon`
- `l2x_repent_and_reform`
- `l2x_leave_mocking_talk`
- `l2x_visible_hidden_sin`
- `l2x_justice_relatives`
- `l2x_speak_without_knowledge`
- `l2x_knowing_betrayal`
- `l2x_company_truthful`
- `l2x_oath_guarantor`
- `l2x_unable_give_kindword`
- `l2x_giving_after_hurt`
- `l2x_gaze_both_groups`
- `l2x_maturity_permission`
- `l2x_eating_and_greeting`
- `l2x_humble_walk_peace`
- `l2x_passing_with_dignity`
- `l2x_prayer_restrains`
- `l2x_spouses_peace_mercy`
- `l2x_parents_limit_kindness`
- `l2x_equal_response_pardon`

Смысловая сверка проведена по проверяемым фактам, а не только по совпадению строк. Важные разграничения:

- Старое17:34 проверяет верность договору; новое3:76 — точное соединение исполнения завета с богобоязненностью.
- Старое2:264 запрещает портить пожертвование упрёком; новые2:267,2:271,2:273 проверяют соответственно качество дара, соотношение открытости/скрытости и признаки сдержанной нужды.
- Старое4:135 спрашивает о справедливости против себя; `l2x_justice_relatives` углублён до пары требований6:152 — справедливой меры и справедливой речи даже о родственнике.
- Старое24:58 перечисляет три времени уединения; новое24:58–59 проверяет переход к общему взрослому правилу после зрелости.
- Новые8:28 и18:46 используют имущество и детей в разных связях: испытание и сопоставление земного украшения с непреходящими делами.
- Первые27 новых beginner сохранены в согласованных темах; прямых повторов их проверяемых фактов в новых110 среднего уровня нет. Смежные темы благодарности и прощения различаются условиями, конкретными связками и контекстом.

При независимом внутреннем просмотре до первого включения в приложение были усилены варианты: убраны ответы, угадываемые только по заведомо плохому или нелепому смыслу; добавлены сопоставимые наставления, близкие пары и последовательности. Это редакционная проверка, не результат пользовательского измерения трудности.

## Карта вопрос → аят

| ID | Аяты | Источник |
|---|---|---|
| `l2x_truth_not_mixed` | 2:42 | [QuranEnc](https://quranenc.com/en/browse/russian_rwwad/2) |
| `l2x_advice_and_self` | 2:44 | [QuranEnc](https://quranenc.com/en/browse/russian_rwwad/2) |
| `l2x_prayer_humility` | 2:45 | [QuranEnc](https://quranenc.com/en/browse/russian_rwwad/2) |
| `l2x_covenant_speech` | 2:83 | [QuranEnc](https://quranenc.com/en/browse/russian_rwwad/2) |
| `l2x_good_sent_ahead` | 2:110 | [QuranEnc](https://quranenc.com/en/browse/russian_rwwad/2) |
| `l2x_remember_and_thank` | 2:152 | [QuranEnc](https://quranenc.com/en/browse/russian_rwwad/2) |
| `l2x_forms_of_trial` | 2:155 | [QuranEnc](https://quranenc.com/en/browse/russian_rwwad/2) |
| `l2x_calamity_return` | 2:156 | [QuranEnc](https://quranenc.com/en/browse/russian_rwwad/2) |
| `l2x_food_and_steps` | 2:168 | [QuranEnc](https://quranenc.com/en/browse/russian_rwwad/2) |
| `l2x_ancestors_reasoning` | 2:170 | [QuranEnc](https://quranenc.com/en/browse/russian_rwwad/2) |
| `l2x_unjust_gain_judges` | 2:188 | [QuranEnc](https://quranenc.com/en/browse/russian_rwwad/2) |
| `l2x_spending_and_ruin` | 2:195 | [QuranEnc](https://quranenc.com/en/browse/russian_rwwad/2) |
| `l2x_best_provision` | 2:197 | [QuranEnc](https://quranenc.com/en/browse/russian_rwwad/2) |
| `l2x_good_both_worlds` | 2:201 | [QuranEnc](https://quranenc.com/en/browse/russian_rwwad/2) |
| `l2x_enter_wholly` | 2:208 | [QuranEnc](https://quranenc.com/en/browse/russian_rwwad/2) |
| `l2x_dislike_and_good` | 2:216 | [QuranEnc](https://quranenc.com/en/browse/russian_rwwad/2) |
| `l2x_faith_no_compulsion` | 2:256 | [QuranEnc](https://quranenc.com/en/browse/russian_rwwad/2) |
| `l2x_quality_of_giving` | 2:267 | [QuranEnc](https://quranenc.com/en/browse/russian_rwwad/2) |
| `l2x_poverty_threat` | 2:268 | [QuranEnc](https://quranenc.com/en/browse/russian_rwwad/2) |
| `l2x_hidden_charity` | 2:271 | [QuranEnc](https://quranenc.com/en/browse/russian_rwwad/2) |
| `l2x_restrained_needy` | 2:273 | [QuranEnc](https://quranenc.com/en/browse/russian_rwwad/2) |
| `l2x_love_and_following` | 3:31 | [QuranEnc](https://quranenc.com/en/browse/russian_rwwad/3) |
| `l2x_covenant_and_piety` | 3:76 | [QuranEnc](https://quranenc.com/en/browse/russian_rwwad/3) |
| `l2x_unity_rope` | 3:103 | [QuranEnc](https://quranenc.com/en/browse/russian_rwwad/3) |
| `l2x_community_call` | 3:104 | [QuranEnc](https://quranenc.com/en/browse/russian_rwwad/3) |
| `l2x_hasten_forgiveness` | 3:133 | [QuranEnc](https://quranenc.com/en/browse/russian_rwwad/3) |
| `l2x_sin_response` | 3:135 | [QuranEnc](https://quranenc.com/en/browse/russian_rwwad/3) |
| `l2x_do_not_lose_heart` | 3:139 | [QuranEnc](https://quranenc.com/en/browse/russian_rwwad/3) |
| `l2x_steadfast_companions` | 3:146 | [QuranEnc](https://quranenc.com/en/browse/russian_rwwad/3) |
| `l2x_help_and_trust` | 3:160 | [QuranEnc](https://quranenc.com/en/browse/russian_rwwad/3) |
| `l2x_hoarding_collar` | 3:180 | [QuranEnc](https://quranenc.com/en/browse/russian_rwwad/3) |
| `l2x_real_success` | 3:185 | [QuranEnc](https://quranenc.com/en/browse/russian_rwwad/3) |
| `l2x_remembrance_reflection` | 3:191 | [QuranEnc](https://quranenc.com/en/browse/russian_rwwad/3) |
| `l2x_patient_perseverance` | 3:200 | [QuranEnc](https://quranenc.com/en/browse/russian_rwwad/3) |
| `l2x_not_covet_shares` | 4:32 | [QuranEnc](https://quranenc.com/en/browse/russian_rwwad/4) |
| `l2x_equal_reward` | 33:35 | [QuranEnc](https://quranenc.com/en/browse/russian_rwwad/33) |
| `l2x_conceal_bounty` | 4:37 | [QuranEnc](https://quranenc.com/en/browse/russian_rwwad/4) |
| `l2x_giving_for_show` | 4:38 | [QuranEnc](https://quranenc.com/en/browse/russian_rwwad/4) |
| `l2x_effective_admonition` | 4:63 | [QuranEnc](https://quranenc.com/en/browse/russian_rwwad/4) |
| `l2x_intercession_share` | 4:85 | [QuranEnc](https://quranenc.com/en/browse/russian_rwwad/4) |
| `l2x_defending_treachery` | 4:107 | [QuranEnc](https://quranenc.com/en/browse/russian_rwwad/4) |
| `l2x_wrong_then_forgiveness` | 4:110 | [QuranEnc](https://quranenc.com/en/browse/russian_rwwad/4) |
| `l2x_blame_innocent` | 4:112 | [QuranEnc](https://quranenc.com/en/browse/russian_rwwad/4) |
| `l2x_good_private_talk` | 4:114 | [QuranEnc](https://quranenc.com/en/browse/russian_rwwad/4) |
| `l2x_reconciliation_good` | 4:128 | [QuranEnc](https://quranenc.com/en/browse/russian_rwwad/4) |
| `l2x_wronged_speech_exception` | 4:148 | [QuranEnc](https://quranenc.com/en/browse/russian_rwwad/4) |
| `l2x_public_hidden_pardon` | 4:149 | [QuranEnc](https://quranenc.com/en/browse/russian_rwwad/4) |
| `l2x_compete_good` | 5:48 | [QuranEnc](https://quranenc.com/en/browse/russian_rwwad/5) |
| `l2x_abundance_not_good` | 5:100 | [QuranEnc](https://quranenc.com/en/browse/russian_rwwad/5) |
| `l2x_lasting_abode` | 6:32 | [QuranEnc](https://quranenc.com/en/browse/russian_rwwad/6) |
| `l2x_repent_and_reform` | 6:54 | [QuranEnc](https://quranenc.com/en/browse/russian_rwwad/6) |
| `l2x_leave_mocking_talk` | 6:68 | [QuranEnc](https://quranenc.com/en/browse/russian_rwwad/6) |
| `l2x_visible_hidden_sin` | 6:120 | [QuranEnc](https://quranenc.com/en/browse/russian_rwwad/6) |
| `l2x_justice_relatives` | 6:152 | [QuranEnc](https://quranenc.com/en/browse/russian_rwwad/6) |
| `l2x_one_path_other_paths` | 6:153 | [QuranEnc](https://quranenc.com/en/browse/russian_rwwad/6) |
| `l2x_no_burden_transfer` | 6:164 | [QuranEnc](https://quranenc.com/en/browse/russian_rwwad/6) |
| `l2x_garment_piety` | 7:26 | [QuranEnc](https://quranenc.com/en/browse/russian_rwwad/7) |
| `l2x_speak_without_knowledge` | 7:33 | [QuranEnc](https://quranenc.com/en/browse/russian_rwwad/7) |
| `l2x_fear_and_hope` | 7:56 | [QuranEnc](https://quranenc.com/en/browse/russian_rwwad/7) |
| `l2x_pardon_right_ignore` | 7:199 | [QuranEnc](https://quranenc.com/en/browse/russian_rwwad/7) |
| `l2x_temptation_remembrance` | 7:201 | [QuranEnc](https://quranenc.com/en/browse/russian_rwwad/7) |
| `l2x_recitation_increases_faith` | 8:2 | [QuranEnc](https://quranenc.com/en/browse/russian_rwwad/8) |
| `l2x_knowing_betrayal` | 8:27 | [QuranEnc](https://quranenc.com/en/browse/russian_rwwad/8) |
| `l2x_wealth_children_trial` | 8:28 | [QuranEnc](https://quranenc.com/en/browse/russian_rwwad/8) |
| `l2x_piety_discernment` | 8:29 | [QuranEnc](https://quranenc.com/en/browse/russian_rwwad/8) |
| `l2x_believers_mutual_support` | 9:71 | [QuranEnc](https://quranenc.com/en/browse/russian_rwwad/9) |
| `l2x_company_truthful` | 9:119 | [QuranEnc](https://quranenc.com/en/browse/russian_rwwad/9) |
| `l2x_healing_hearts` | 10:57 | [QuranEnc](https://quranenc.com/en/browse/russian_rwwad/10) |
| `l2x_joy_bounty_mercy` | 10:58 | [QuranEnc](https://quranenc.com/en/browse/russian_rwwad/10) |
| `l2x_good_erases_bad` | 11:114 | [QuranEnc](https://quranenc.com/en/browse/russian_rwwad/11) |
| `l2x_patience_for_lord` | 13:22 | [QuranEnc](https://quranenc.com/en/browse/russian_rwwad/13) |
| `l2x_gratitude_increase` | 14:7 | [QuranEnc](https://quranenc.com/en/browse/russian_rwwad/14) |
| `l2x_wrongdoing_delay` | 14:42 | [QuranEnc](https://quranenc.com/en/browse/russian_rwwad/14) |
| `l2x_lower_wing_believers` | 15:88 | [QuranEnc](https://quranenc.com/en/browse/russian_rwwad/15) |
| `l2x_oath_guarantor` | 16:91 | [QuranEnc](https://quranenc.com/en/browse/russian_rwwad/16) |
| `l2x_unravelled_yarn` | 16:92 | [QuranEnc](https://quranenc.com/en/browse/russian_rwwad/16) |
| `l2x_perishes_remains` | 16:96 | [QuranEnc](https://quranenc.com/en/browse/russian_rwwad/16) |
| `l2x_faith_deeds_goodlife` | 16:97 | [QuranEnc](https://quranenc.com/en/browse/russian_rwwad/16) |
| `l2x_quran_upright_guidance` | 17:9 | [QuranEnc](https://quranenc.com/en/browse/russian_rwwad/17) |
| `l2x_unable_give_kindword` | 17:28 | [QuranEnc](https://quranenc.com/en/browse/russian_rwwad/17) |
| `l2x_faculties_accountable` | 17:36 | [QuranEnc](https://quranenc.com/en/browse/russian_rwwad/17) |
| `l2x_best_words_discord` | 17:53 | [QuranEnc](https://quranenc.com/en/browse/russian_rwwad/17) |
| `l2x_company_morning_evening` | 18:28 | [QuranEnc](https://quranenc.com/en/browse/russian_rwwad/18) |
| `l2x_adornment_enduring_deeds` | 18:46 | [QuranEnc](https://quranenc.com/en/browse/russian_rwwad/18) |
| `l2x_glitter_and_provision` | 20:131 | [QuranEnc](https://quranenc.com/en/browse/russian_rwwad/20) |
| `l2x_good_and_bad_tests` | 21:35 | [QuranEnc](https://quranenc.com/en/browse/russian_rwwad/21) |
| `l2x_sacrifice_reaches` | 22:37 | [QuranEnc](https://quranenc.com/en/browse/russian_rwwad/22) |
| `l2x_giving_with_awe` | 23:60, 23:61 | [QuranEnc](https://quranenc.com/en/browse/russian_rwwad/23) |
| `l2x_repel_with_best` | 23:96 | [QuranEnc](https://quranenc.com/en/browse/russian_rwwad/23) |
| `l2x_giving_after_hurt` | 24:22 | [QuranEnc](https://quranenc.com/en/browse/russian_rwwad/24) |
| `l2x_gaze_both_groups` | 24:30, 24:31 | [QuranEnc](https://quranenc.com/en/browse/russian_rwwad/24) |
| `l2x_maturity_permission` | 24:58, 24:59 | [QuranEnc](https://quranenc.com/en/browse/russian_rwwad/24) |
| `l2x_eating_and_greeting` | 24:61 | [QuranEnc](https://quranenc.com/en/browse/russian_rwwad/24) |
| `l2x_humble_walk_peace` | 25:63 | [QuranEnc](https://quranenc.com/en/browse/russian_rwwad/25) |
| `l2x_passing_with_dignity` | 25:72 | [QuranEnc](https://quranenc.com/en/browse/russian_rwwad/25) |
| `l2x_not_deaf_blind` | 25:73 | [QuranEnc](https://quranenc.com/en/browse/russian_rwwad/25) |
| `l2x_family_and_example` | 25:74 | [QuranEnc](https://quranenc.com/en/browse/russian_rwwad/25) |
| `l2x_double_reward_patience` | 28:54 | [QuranEnc](https://quranenc.com/en/browse/russian_rwwad/28) |
| `l2x_prayer_restrains` | 29:45 | [QuranEnc](https://quranenc.com/en/browse/russian_rwwad/29) |
| `l2x_spouses_peace_mercy` | 30:21 | [QuranEnc](https://quranenc.com/en/browse/russian_rwwad/30) |
| `l2x_giving_recipient_trio` | 30:38 | [QuranEnc](https://quranenc.com/en/browse/russian_rwwad/30) |
| `l2x_gratitude_benefits_self` | 31:12 | [QuranEnc](https://quranenc.com/en/browse/russian_rwwad/31) |
| `l2x_parents_limit_kindness` | 31:15 | [QuranEnc](https://quranenc.com/en/browse/russian_rwwad/31) |
| `l2x_mustard_hidden_locations` | 31:16 | [QuranEnc](https://quranenc.com/en/browse/russian_rwwad/31) |
| `l2x_upright_speech_results` | 33:70, 33:71 | [QuranEnc](https://quranenc.com/en/browse/russian_rwwad/33) |
| `l2x_words_and_deeds_rise` | 35:10 | [QuranEnc](https://quranenc.com/en/browse/russian_rwwad/35) |
| `l2x_night_fear_hope` | 39:9 | [QuranEnc](https://quranenc.com/en/browse/russian_rwwad/39) |
| `l2x_enmity_close_friend` | 41:34 | [QuranEnc](https://quranenc.com/en/browse/russian_rwwad/41) |
| `l2x_consultation_and_worship` | 42:38 | [QuranEnc](https://quranenc.com/en/browse/russian_rwwad/42) |
| `l2x_equal_response_pardon` | 42:40 | [QuranEnc](https://quranenc.com/en/browse/russian_rwwad/42) |

## Закреплённые исходные файлы

Ниже Git blob SHA-1 для файлов `russian_rwwad/<сура>.json` указанного исходного коммита. Это идентификаторы Git-объектов, не SHA-256 APK или подписи.

| Сура | Git blob SHA-1 |
|---|---|
| 2 | `f07351616a53eb2fb0b0c5060fc5dec051ddf263` |
| 3 | `962b0dbeb44792200da8f6d70c5c9153d3e0cc7b` |
| 4 | `625f0d83dca90dd321ee7e3d78bd39082f3df4fd` |
| 5 | `77a5af5ad2c7f93ff18a1749a3df87d4965487c1` |
| 6 | `8de42af424ce83b71ccbf86e4f2b277b0bd8ecf1` |
| 7 | `5616042ddfc5ea4518fcd7f96d71918d80a6141c` |
| 8 | `6c8243e67e123267fddf3987808b1f9e8d45fe11` |
| 9 | `1eeb3a36ca7fda313aca945f1cf0d3a5e5e86643` |
| 10 | `8004d414985ea392b93ebf47e662a6281713b444` |
| 11 | `877e733c99ae5d728664ee4a17465c5784d17c0f` |
| 13 | `b087d7eedf528f9bdb04cc8d25c02c0e8aaef42d` |
| 14 | `f0b01880b15386a5e7cf3efe256ddc1b7b41c742` |
| 15 | `2e18ad9e25a384a8d7be6056add81fcd3bbce3c4` |
| 16 | `fda565c1950d6491a22b6bf1c0805cd35081e0d6` |
| 17 | `be2b51e80557ce1a1e93c6078be9a2bf74e74df7` |
| 18 | `6ddb7313764466acabdd6f5362d814b067e6c28d` |
| 20 | `9c7835c5c88a1106d279743c117382c066c43ee6` |
| 21 | `490b748ad2e05051ede04ccb4451d78855f121e2` |
| 22 | `a936585e9c8adbb72474467a0b0fd185adf15208` |
| 23 | `920228850e2346dec00b2b951a8c7a068c12b4b7` |
| 24 | `899313c5d87434db6f6b7c49366bfaf0ec3b5c92` |
| 25 | `a504eafaebd8c15f60a890004e838c6cf2b3d735` |
| 28 | `4a171c1501f697b3ef20e54cbb50ca29876bb4b0` |
| 29 | `8a039d5204105a1254b65a4d09635e0388c4e8ba` |
| 30 | `208440f45d078b2fd541c318a7321ba5055c0a33` |
| 31 | `281fdb7fca8020ba371b95456f60ee18ad3c321b` |
| 33 | `e9c085a5e2f20702811d0a2e2a22dfc53df686bf` |
| 35 | `c244db1585cc3d27aba40ee42153b2939730d096` |
| 39 | `14148a80cff8e51da9f61e62a0cfbfcd9e7050df` |
| 41 | `7f1cb5e60770de7a5dff3ae6e74f1e5c785a1fc1` |
| 42 | `7b42908a528d3a1b0d586e40bfc17324c5c26069` |
