"""Import dated CDUM HTML, Nizhny Novgorod PDF and Saratov HTML. Needs lxml and pypdf."""

import argparse
import calendar
import io
import re
from datetime import date, timedelta
from pathlib import Path
from urllib.parse import urljoin, quote
from urllib.request import urlopen

from lxml import html
from pypdf import PdfReader

MONTHS = "Январь Февраль Март Апрель Май Июнь Июль Август Сентябрь Октябрь Ноябрь Декабрь".split()
WEEK = "Пн Вт Ср Чт Пт Сб Вс".split()
CITIES = {
    "saint_petersburg": "Санкт-Петербург", "astrakhan": "Астрахань", "kirov": "Киров",
    "yekaterinburg": "Екатеринбург", "izhevsk": "Ижевск", "yoshkar_ola": "Йошкар-Ола",
    "volgograd": "Волгоград", "kurgan": "Курган", "orenburg": "Оренбург", "penza": "Пенза",
    "perm": "Пермь", "rostov_on_don": "Ростов-на-Дону", "salekhard": "Салехард",
    "samara": "Самара", "ulyanovsk": "Ульяновск", "khabarovsk": "Хабаровск",
    "cheboksary": "Чебоксары", "chelyabinsk": "Челябинск",
}
CDUM = "https://www.cdum.ru/time-namaz/Spb.php"


def require(condition, message):
    if not condition:
        raise ValueError(message)


def read_source(folder, filename, url):
    path = folder / filename
    if not path.exists():
        with urlopen(url, timeout=35) as response:
            path.write_bytes(response.read())
    return path.read_bytes()


def document(content):
    return html.fromstring(content, parser=html.HTMLParser(encoding="utf-8"))


def words(element):
    return " ".join(element.text_content().split())


def clock(text):
    # Some CDUM times carry a footnote asterisk after the actual time.
    match = re.fullmatch(r"(\d{1,2})[:.](\d{2})\*?", text)
    require(match, f"Unrecognised time {text!r}")
    hour, minute = map(int, match.groups())
    require(hour < 24 and minute < 60, f"Invalid time {text!r}")
    return f"{hour:02}:{minute:02}"


def dated(year, month, day, weekday):
    value = date(year, month, int(day))
    require(WEEK[value.weekday()] == weekday.rstrip("."), f"Weekday mismatch {value}: {weekday}")
    return value


def add(rows, day, values):
    values = tuple(map(clock, values))
    require(len(values) == 6, f"Expected six times on {day}")
    require(day not in rows or rows[day] == values, f"Conflicting entries on {day}")
    rows[day] = values


def cdum_year(content, name, year):
    root = document(content)
    require([words(h) for h in root.xpath("//h1")] == [name], f"Wrong city: {name}")
    require(any(words(a) == f"{name} {year}" for a in root.xpath("//a")), f"Year missing: {name}")
    found = {}
    headers = root.xpath('//tr[td[1][normalize-space(string(.))="Дата"]]')
    for head in headers:
        labels = [words(c) for c in head.xpath("./td")]
        require(labels == ["Дата", "Фаджр", "Восход", "Зухр", "Аср", "Магриб", "Иша"], f"Changed columns: {name}")
        label = head.xpath('preceding::text()[normalize-space(translate(., " "," "))!=""][1]')[0].strip()
        month = MONTHS.index(label) + 1
        count = calendar.monthrange(year, month)[1]
        for row in head.itersiblings("tr"):
            values = [words(c) for c in row.xpath("./td")]
            if len(values) != 7 or not re.fullmatch(r"\d+ [А-Яа-я]{2}\.", values[0]):
                break
            day, weekday = values[0].split()
            add(found, dated(year, month, day, weekday), values[1:])
        require(all(date(year, month, d) in found for d in range(1, count+1)), f"Incomplete {name} {month}")
    require(len(found) == 365 + calendar.isleap(year), f"Incomplete year for {name}")
    return found


def nizhny_year(content, year):
    pdf = PdfReader(io.BytesIO(content))
    found = {}
    for month, page in enumerate(pdf.pages[2:14], 1):
        text = page.extract_text().replace("Cр ", "Ср ")
        require(re.match(rf"{MONTHS[month-1]},\s*{year}\b", text), "Unexpected PDF page/year")
        require(all(label in text[:230] for label in ("ФАДЖР", "ЗЕНИТ", "ЗУХР", "АСР", "МАГРИБ", "ИША")), "Changed PDF columns")
        for line in text.splitlines():
            tokens = line.split()
            if len(tokens) < 2 or tokens[0] not in WEEK or not tokens[1].isdigit():
                continue
            require(len(tokens) == 10, f"Changed PDF row: {line}")
            # The separate zenith column precedes the published Dhuhr start.
            add(found, dated(year, month, tokens[1], tokens[0]), [tokens[i] for i in (2, 3, 5, 6, 7, 8)])
    require(len(found) == 365 + calendar.isleap(year), "Incomplete Nizhny Novgorod year")
    return found


def saratov_month(content, year):
    root = document(content)
    today = root.xpath('//td[@id="today"]/a/@href')
    require(len(today) == 1 and re.search(rf"/{year}/\d{{2}}/\d{{2}}/?$", today[0]), "Undated Saratov page")
    today = date.fromisoformat(today[0].rstrip("/")[-10:].replace("/", "-"))
    require(any(words(h).lower() == "расписание намазов на " + MONTHS[today.month-1].lower()
                for h in root.xpath("//h2")), "Wrong Saratov month")
    table, = root.xpath('//table[@class="namaz_time"]')
    header, *body = table.xpath(".//tr")
    labels = [words(c).lower() for c in header.xpath("./td")]
    require(len(labels) == 9 and all(s in t for s, t in zip(("фажр","восход","зухр","аср","магриб","ийша"), labels[3:])), "Changed Saratov header")
    week = ["Понедел", "Вторник", "Среда", "Четверг", "Пятница", "Суббота", "Воскрес"]
    found = {}
    for r in body:
        v = [words(c) for c in r.xpath("./td")]
        require(len(v) == 9, "Changed Saratov row")
        day = date(year, today.month, int(v[0]))
        # In this row the site prints a Hijri month label in the weekday cell.
        # The Gregorian day, six prayer fields, previous and following civil days are explicit.
        known_label = day == date(2026, 11, 19) and v[1] == "Джумада"
        require(v[1].startswith(week[day.weekday()]) or known_label, f"Saratov weekday mismatch: {day}")
        add(found, day, v[3:])
    require(len(found) == calendar.monthrange(year, today.month)[1], "Incomplete Saratov month")
    return found


def validated_rows(rows, city):
    def minutes(clock_time):
        hours, mins = map(int, clock_time.split(":"))
        return hours * 60 + mins
    instants = {}
    for day, times in rows.items():
        f, s, d, a, m, i = map(minutes, times)
        if f > s and f >= 20 * 60 and s < 12 * 60:
            f -= 24 * 60
        if i < m and i < 6 * 60 and m > 12 * 60:
            i += 24 * 60
        if f < s < d < a < m < i:
            instants[day] = (f, i)
    rejected = set(rows) - set(instants)
    for day, (fajr, _) in instants.items():
        prev = day - timedelta(days=1)
        if prev in instants and instants[prev][1] >= fajr + 24 * 60:
            rejected.update([prev, day])
    if rejected:
        print(f"{city}: excluded {len(rejected)} overlapping or unordered days: {sorted(map(str, rejected))}")
    return [f"{city}\t{day}\t" + "\t".join(values) for day, values in rows.items() if day not in rejected]


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument("year", type=int)
    p.add_argument("cache", type=Path, help="Source snapshots; absent files are downloaded over HTTPS")
    p.add_argument("output", type=Path)
    args = p.parse_args()
    args.cache.mkdir(parents=True, exist_ok=True)
    year = args.year
    index = document(read_source(args.cache, "cdum.html", CDUM))
    lines = []
    for city, name in CITIES.items():
        link, = {a.get("href") for a in index.xpath("//a[@href]") if words(a) == f"{name} {year}"}
        url = urljoin(CDUM, quote(link, safe="/%"))
        data = read_source(args.cache, f"cdum-{city}.html", url)
        lines.extend(validated_rows(cdum_year(data, name, year), city))
    pdf = read_source(args.cache, "nn.pdf", f"https://islamnn.ru/namazy/{year}/nn{year}.pdf")
    lines.extend(validated_rows(nizhny_year(pdf, year), "nizhny_novgorod"))
    pages = sorted(args.cache.glob("saratov-*.html"))
    if not pages:
        read_source(args.cache, "saratov.html", "https://dumso.ru/raspisanie")
        pages = [args.cache / "saratov.html"]
    saratov = {}
    for path in pages:
        for day, values in saratov_month(path.read_bytes(), year).items():
            add(saratov, day, values)
    lines.extend(validated_rows(saratov, "saratov"))
    args.output.write_bytes(("\n".join(sorted(lines)) + "\n").encode("utf-8"))
    print(f"{len(lines)} dated rows written to {args.output}")


if __name__ == "__main__":
    main()
