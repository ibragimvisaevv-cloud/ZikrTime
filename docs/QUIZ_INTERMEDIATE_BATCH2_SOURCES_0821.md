# Средний уровень: вторая группа 0.8.21

Файл: `tools/quiz/levels/intermediate_batch2_0821.json`. 40 новых вопросов; правильный вариант в авторском банке расположен под индексом 0, при показе приложение перемешивает варианты.

## Проверка содержания

Для каждого вопроса прочитан указанный аят в закреплённом переводе QuranEnc / Rowwad из `app/src/main/assets/quran/sources/russian_rwwad`, исходный коммит `c592403a83b5455719b35dc1570399406e676efa`. Использованы суры 4, 5, 6, 7, 8, 9, 10, 11, 13, 14, 15, 16 и 17. Смысл правильного ответа проверялся непосредственно по этим данным, а не только по наличию номера аята.

В 25 вопросах до перевода уточнён контекст или заменены отвлекающие варианты: варианты со случайными бытовыми условиями заменены сопоставимыми религиозными понятиями и наставлениями. Например, 8:2 спрашивает о последовательности трёх признаков; 6:152 — о паре требований; 16:97 — о втором обещании после благой жизни. Неверные варианты не считаются утверждениями приложения: вопрос указывает точный аят, а пояснение содержит проверенный смысл.

Ни один из 40 используемых аятов не повторяется в прежних 40 вопросах среднего уровня 0.8.20. Тематическая близость сама по себе не приравнивается к тождеству факта.

Выборочная онлайн-сверка выполнена с официальным порталом Diyanet: [Ниса, 110](https://kuran.diyanet.gov.tr/tefsir/Nis%C3%A2-suresi/603/110-ayet-tefsiri), [Ниса, аяты 106–113](https://kuran.diyanet.gov.tr/mushaf/kuran-meal-2/nisa-suresi-4/ayet-106/diyanet-isleri-baskanligi-meali-1), а также с [QuranEnc, ан-Нахль 94–97](https://quranenc.com/de/browse/russian_rwwad/16-12). Полная фактическая сверка основана на закреплённых аятах; полная онлайн-проверка всех языков не заявляется.

Вопросы, варианты и пояснения — авторские формулировки приложения. Переводы не выдаются за опубликованные переводы аятов и не имеют выдуманных отметок независимой богословской или профессиональной языковой экспертизы.

## Аяты

| ID | Источник |
|---|---|
| l2x_defending_treachery | 4:107 |
| l2x_wrong_then_forgiveness | 4:110 |
| l2x_blame_innocent | 4:112 |
| l2x_good_private_talk | 4:114 |
| l2x_reconciliation_good | 4:128 |
| l2x_wronged_speech_exception | 4:148 |
| l2x_public_hidden_pardon | 4:149 |
| l2x_compete_good | 5:48 |
| l2x_abundance_not_good | 5:100 |
| l2x_lasting_abode | 6:32 |
| l2x_repent_and_reform | 6:54 |
| l2x_leave_mocking_talk | 6:68 |
| l2x_visible_hidden_sin | 6:120 |
| l2x_justice_relatives | 6:152 |
| l2x_one_path_other_paths | 6:153 |
| l2x_no_burden_transfer | 6:164 |
| l2x_garment_piety | 7:26 |
| l2x_speak_without_knowledge | 7:33 |
| l2x_fear_and_hope | 7:56 |
| l2x_pardon_right_ignore | 7:199 |
| l2x_temptation_remembrance | 7:201 |
| l2x_recitation_increases_faith | 8:2 |
| l2x_knowing_betrayal | 8:27 |
| l2x_wealth_children_trial | 8:28 |
| l2x_piety_discernment | 8:29 |
| l2x_believers_mutual_support | 9:71 |
| l2x_company_truthful | 9:119 |
| l2x_healing_hearts | 10:57 |
| l2x_joy_bounty_mercy | 10:58 |
| l2x_good_erases_bad | 11:114 |
| l2x_patience_for_lord | 13:22 |
| l2x_gratitude_increase | 14:7 |
| l2x_wrongdoing_delay | 14:42 |
| l2x_lower_wing_believers | 15:88 |
| l2x_oath_guarantor | 16:91 |
| l2x_unravelled_yarn | 16:92 |
| l2x_perishes_remains | 16:96 |
| l2x_faith_deeds_goodlife | 16:97 |
| l2x_quran_upright_guidance | 17:9 |
| l2x_unable_give_kindword | 17:28 |

## Проверки перед включением

Для всех локалей проверяются наличие вопроса и пояснения, четыре различных варианта, неизменный порядок смыслов, отсутствие английских копий вместо перевода. Ссылки, ID и correct index 0 сохраняются. Итоговые проверки генератора и запуска приложения отражаются в общем отчёте сборки.
