"""Список городов с названиями на 8 языках → app/src/main/assets/cities.json (общий для Android и iOS).

Порядок названий: ru | en | uz | tg | kk | ky | az | tr
"""
import json
import pathlib

LANGS = ["ru", "en", "uz", "tg", "kk", "ky", "az", "tr"]

GROUPS = [
    ("russia", "Россия|Russia|Rossiya|Русия|Ресей|Россия|Rusiya|Rusya"),
    ("uzbekistan", "Узбекистан|Uzbekistan|Oʻzbekiston|Ӯзбекистон|Өзбекстан|Өзбекстан|Özbəkistan|Özbekistan"),
    ("tajikistan", "Таджикистан|Tajikistan|Tojikiston|Тоҷикистон|Тәжікстан|Тажикстан|Tacikistan|Tacikistan"),
    ("kazakhstan", "Казахстан|Kazakhstan|Qozogʻiston|Қазоқистон|Қазақстан|Казакстан|Qazaxıstan|Kazakistan"),
    ("kyrgyzstan", "Кыргызстан|Kyrgyzstan|Qirgʻiziston|Қирғизистон|Қырғызстан|Кыргызстан|Qırğızıstan|Kırgızistan"),
    ("azerbaijan", "Азербайджан|Azerbaijan|Ozarbayjon|Озарбойҷон|Әзербайжан|Азербайжан|Azərbaycan|Azerbaycan"),
    ("turkey", "Турция|Türkiye|Turkiya|Туркия|Түркия|Түркия|Türkiyə|Türkiye"),
    ("arabia", "Саудовская Аравия|Saudi Arabia|Saudiya Arabistoni|Арабистони Саудӣ|Сауд Арабиясы|Сауд Арабиясы|Səudiyyə Ərəbistanı|Suudi Arabistan"),
    ("world", "Другие города|Other cities|Boshqa shaharlar|Шаҳрҳои дигар|Басқа қалалар|Башка шаарлар|Digər şəhərlər|Diğer şehirler"),
]

CITIES = [
    # Россия
    ("moscow", "russia", 55.7558, 37.6173, "Москва|Moscow|Moskva|Маскав|Мәскеу|Москва|Moskva|Moskova"),
    ("saint_petersburg", "russia", 59.9343, 30.3351, "Санкт-Петербург|Saint Petersburg|Sankt-Peterburg|Санкт-Петербург|Санкт-Петербург|Санкт-Петербург|Sankt-Peterburq|Sankt-Peterburg"),
    ("grozny", "russia", 43.3178, 45.6949, "Грозный|Grozny|Grozniy|Грозний|Грозный|Грозный|Qroznı|Grozni"),
    ("gudermes", "russia", 43.3519, 46.1003, "Гудермес|Gudermes|Gudermes|Гудермес|Гудермес|Гудермес|Qudermes|Gudermes"),
    ("makhachkala", "russia", 42.9849, 47.5047, "Махачкала|Makhachkala|Mahachqala|Махачқалъа|Махачкала|Махачкала|Mahaçqala|Mahaçkale"),
    ("khasavyurt", "russia", 43.2500, 46.5833, "Хасавюрт|Khasavyurt|Xasavyurt|Хасавюрт|Хасавюрт|Хасавюрт|Xasavyurt|Hasavyurt"),
    ("derbent", "russia", 42.0678, 48.2899, "Дербент|Derbent|Darband|Дарбанд|Дербент|Дербент|Dərbənd|Derbent"),
    ("nazran", "russia", 43.2167, 44.7667, "Назрань|Nazran|Nazran|Назран|Назрань|Назрань|Nazran|Nazran"),
    ("nalchik", "russia", 43.4853, 43.6071, "Нальчик|Nalchik|Nalchik|Налчик|Нальчик|Нальчик|Nalçik|Nalçik"),
    ("vladikavkaz", "russia", 43.0367, 44.6678, "Владикавказ|Vladikavkaz|Vladikavkaz|Владикавказ|Владикавказ|Владикавказ|Vladiqafqaz|Vladikavkaz"),
    ("cherkessk", "russia", 44.2269, 42.0578, "Черкесск|Cherkessk|Cherkessk|Черкесск|Черкесск|Черкесск|Çerkessk|Çerkessk"),
    ("maykop", "russia", 44.6098, 40.1006, "Майкоп|Maykop|Maykop|Майкоп|Майкоп|Майкоп|Maykop|Maykop"),
    ("kazan", "russia", 55.7963, 49.1088, "Казань|Kazan|Qozon|Қазон|Қазан|Казань|Kazan|Kazan"),
    ("ufa", "russia", 54.7388, 55.9721, "Уфа|Ufa|Ufa|Уфа|Уфа|Уфа|Ufa|Ufa"),
    ("astrakhan", "russia", 46.3497, 48.0408, "Астрахань|Astrakhan|Astraxan|Астрахан|Астрахан|Астрахань|Həştərxan|Astrahan"),
    ("nizhny_novgorod", "russia", 56.2965, 43.9361, "Нижний Новгород|Nizhny Novgorod|Nijniy Novgorod|Нижний Новгород|Нижний Новгород|Нижний Новгород|Nijni Novqorod|Nijniy Novgorod"),
    ("samara", "russia", 53.1959, 50.1002, "Самара|Samara|Samara|Самара|Самара|Самара|Samara|Samara"),
    ("yekaterinburg", "russia", 56.8389, 60.6057, "Екатеринбург|Yekaterinburg|Yekaterinburg|Екатеринбург|Екатеринбург|Екатеринбург|Yekaterinburq|Yekaterinburg"),
    ("novosibirsk", "russia", 55.0084, 82.9357, "Новосибирск|Novosibirsk|Novosibirsk|Новосибирск|Новосібір|Новосибирск|Novosibirsk|Novosibirsk"),
    # Узбекистан
    ("tashkent", "uzbekistan", 41.2995, 69.2401, "Ташкент|Tashkent|Toshkent|Тошкент|Ташкент|Ташкент|Daşkənd|Taşkent"),
    ("samarkand", "uzbekistan", 39.6542, 66.9597, "Самарканд|Samarkand|Samarqand|Самарқанд|Самарқанд|Самарканд|Səmərqənd|Semerkant"),
    ("bukhara", "uzbekistan", 39.7681, 64.4556, "Бухара|Bukhara|Buxoro|Бухоро|Бұхара|Бухара|Buxara|Buhara"),
    ("namangan", "uzbekistan", 40.9983, 71.6726, "Наманган|Namangan|Namangan|Намангон|Наманган|Наманган|Namanqan|Nemengen"),
    ("andijan", "uzbekistan", 40.7821, 72.3442, "Андижан|Andijan|Andijon|Андиҷон|Әндіжан|Анжиян|Əndican|Andican"),
    ("fergana", "uzbekistan", 40.3864, 71.7864, "Фергана|Fergana|Fargʻona|Фарғона|Ферғана|Фергана|Fərqanə|Fergana"),
    ("nukus", "uzbekistan", 42.4600, 59.6100, "Нукус|Nukus|Nukus|Нукус|Нөкіс|Нукус|Nukus|Nukus"),
    ("qarshi", "uzbekistan", 38.8606, 65.7891, "Карши|Qarshi|Qarshi|Қаршӣ|Қарші|Карши|Qarşı|Karşı"),
    ("urgench", "uzbekistan", 41.5500, 60.6333, "Ургенч|Urgench|Urganch|Урганҷ|Үргеніш|Ургенч|Urgenç|Ürgenç"),
    # Таджикистан
    ("dushanbe", "tajikistan", 38.5598, 68.7870, "Душанбе|Dushanbe|Dushanbe|Душанбе|Душанбе|Душанбе|Düşənbə|Duşanbe"),
    ("khujand", "tajikistan", 40.2826, 69.6220, "Худжанд|Khujand|Xoʻjand|Хуҷанд|Хұжанд|Хужанд|Xocənd|Hucend"),
    ("kulob", "tajikistan", 37.9146, 69.7845, "Куляб|Kulob|Kulob|Кӯлоб|Күлаб|Куляб|Kulyab|Külab"),
    ("bokhtar", "tajikistan", 37.8364, 68.7803, "Бохтар|Bokhtar|Bohtar|Бохтар|Бохтар|Бохтар|Boxtar|Bohtar"),
    ("istaravshan", "tajikistan", 39.9108, 69.0064, "Истаравшан|Istaravshan|Istaravshan|Истаравшан|Истаравшан|Истаравшан|İstaravşan|İstaravşan"),
    ("khorog", "tajikistan", 37.4897, 71.5531, "Хорог|Khorog|Xorugʻ|Хоруғ|Хорог|Хорог|Xoroq|Horog"),
    # Казахстан
    ("almaty", "kazakhstan", 43.2220, 76.8512, "Алматы|Almaty|Olmaota|Алмаато|Алматы|Алматы|Almatı|Almatı"),
    ("astana", "kazakhstan", 51.1694, 71.4491, "Астана|Astana|Ostona|Остона|Астана|Астана|Astana|Astana"),
    ("shymkent", "kazakhstan", 42.3417, 69.5901, "Шымкент|Shymkent|Chimkent|Чимкент|Шымкент|Шымкент|Çimkənd|Çimkent"),
    ("aktobe", "kazakhstan", 50.2839, 57.1670, "Актобе|Aktobe|Aqtoʻbe|Ақтӯбе|Ақтөбе|Актөбө|Aktöbe|Aktöbe"),
    ("karaganda", "kazakhstan", 49.8047, 73.1094, "Караганда|Karaganda|Qaragʻandi|Қарағанда|Қарағанды|Караганды|Qaraqanda|Karaganda"),
    ("taraz", "kazakhstan", 42.9000, 71.3667, "Тараз|Taraz|Taroz|Тароз|Тараз|Тараз|Taraz|Taraz"),
    ("turkistan", "kazakhstan", 43.2973, 68.2518, "Туркестан|Turkistan|Turkiston|Туркистон|Түркістан|Түркстан|Türküstan|Türkistan"),
    ("atyrau", "kazakhstan", 47.1167, 51.8833, "Атырау|Atyrau|Atirau|Атирав|Атырау|Атырау|Atırau|Atırav"),
    # Кыргызстан
    ("bishkek", "kyrgyzstan", 42.8746, 74.5698, "Бишкек|Bishkek|Bishkek|Бишкек|Бішкек|Бишкек|Bişkek|Bişkek"),
    ("osh", "kyrgyzstan", 40.5283, 72.7985, "Ош|Osh|Oʻsh|Ӯш|Ош|Ош|Oş|Oş"),
    ("jalal_abad", "kyrgyzstan", 40.9333, 73.0000, "Джалал-Абад|Jalal-Abad|Jalolobod|Ҷалолобод|Жалалабад|Жалал-Абад|Calal-Abad|Celalabad"),
    ("karakol", "kyrgyzstan", 42.4907, 78.3936, "Каракол|Karakol|Qoraqoʻl|Қарақӯл|Қарақол|Каракол|Qaraqol|Karakol"),
    # Азербайджан
    ("baku", "azerbaijan", 40.4093, 49.8671, "Баку|Baku|Boku|Боку|Баку|Баку|Bakı|Bakü"),
    ("ganja", "azerbaijan", 40.6828, 46.3606, "Гянджа|Ganja|Ganja|Ганҷа|Гәнжә|Гянджа|Gəncə|Gence"),
    ("sumgait", "azerbaijan", 40.5897, 49.6686, "Сумгаит|Sumgait|Sumqayit|Сумқаит|Сумгайыт|Сумгаит|Sumqayıt|Sumgayıt"),
    # Турция
    ("istanbul", "turkey", 41.0082, 28.9784, "Стамбул|Istanbul|Istanbul|Истанбул|Стамбұл|Стамбул|İstanbul|İstanbul"),
    ("ankara", "turkey", 39.9334, 32.8597, "Анкара|Ankara|Anqara|Анқара|Анкара|Анкара|Ankara|Ankara"),
    ("bursa", "turkey", 40.1885, 29.0610, "Бурса|Bursa|Bursa|Бурса|Бурса|Бурса|Bursa|Bursa"),
    # Саудовская Аравия
    ("mecca", "arabia", 21.3891, 39.8579, "Мекка|Mecca|Makka|Макка|Мекке|Мекке|Məkkə|Mekke"),
    ("medina", "arabia", 24.5247, 39.5692, "Медина|Medina|Madina|Мадина|Медине|Медина|Mədinə|Medine"),
    # Другие
    ("cairo", "world", 30.0444, 31.2357, "Каир|Cairo|Qohira|Қоҳира|Каир|Каир|Qahirə|Kahire"),
    ("dubai", "world", 25.2048, 55.2708, "Дубай|Dubai|Dubay|Дубай|Дубай|Дубай|Dubay|Dubai"),
    ("london", "world", 51.5074, -0.1278, "Лондон|London|London|Лондон|Лондон|Лондон|London|Londra"),
    ("berlin", "world", 52.5200, 13.4050, "Берлин|Berlin|Berlin|Берлин|Берлин|Берлин|Berlin|Berlin"),
    ("amsterdam", "world", 52.3676, 4.9041, "Амстердам|Amsterdam|Amsterdam|Амстердам|Амстердам|Амстердам|Amsterdam|Amsterdam"),
    ("paris", "world", 48.8566, 2.3522, "Париж|Paris|Parij|Париж|Париж|Париж|Paris|Paris"),
    ("new_york", "world", 40.7128, -74.0060, "Нью-Йорк|New York|Nyu-York|Ню-Йорк|Нью-Йорк|Нью-Йорк|Nyu-York|New York"),
]


def names(spec):
    parts = spec.split("|")
    assert len(parts) == len(LANGS), spec
    return dict(zip(LANGS, parts))


def main():
    groups = [{"id": gid, "names": names(n)} for gid, n in GROUPS]
    gids = {g["id"] for g in groups}
    ids = set()
    cities = []
    for cid, gid, lat, lon, n in CITIES:
        assert gid in gids, cid
        assert cid not in ids, cid
        ids.add(cid)
        cities.append({"id": cid, "group": gid, "lat": lat, "lon": lon, "names": names(n)})
    out = pathlib.Path(__file__).resolve().parents[2] / "app/src/main/assets/cities.json"
    out.write_text(json.dumps({"groups": groups, "cities": cities}, ensure_ascii=False, indent=1), encoding="utf-8")
    print(len(cities), "cities in", len(groups), "groups")


if __name__ == "__main__":
    main()
