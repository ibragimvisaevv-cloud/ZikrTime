"""Preserve already verified Android evidence; never rebuild the application."""
from pathlib import Path
import hashlib, json, os, shutil, subprocess
from PIL import Image, ImageDraw, ImageFont

REPO='ibragimvisaevv-cloud/ZikrTime'
ROOT=Path('evidence-input')
OUT=Path('design/review-0811');OUT.mkdir(parents=True,exist_ok=True)
sources=[
 ('before',40906009119,12911119593,'Android-whole-tour','09543f406b6bf3b2f8b5384d148312f581e5d486'),
 ('build',40922530810,12919755279,'Android-natural-build','c45fad473e3ab093d3c9bfb69afdb710813d42df'),
 ('composition',40922530810,12918534963,'Android-whole-composition','c45fad473e3ab093d3c9bfb69afdb710813d42df'),
 ('journeys',40919659229,12919470083,'Android-whole-journeys','675619b2b11b77d49ffcf5c8f25c89781bd3d67b'),
 ('accessibility',40919659229,12919130258,'Android-whole-accessibility','675619b2b11b77d49ffcf5c8f25c89781bd3d67b'),
 ('focus',40919659229,12919255271,'Android-whole-focus','675619b2b11b77d49ffcf5c8f25c89781bd3d67b'),
]
def run(args):subprocess.run(args,check=True)
manifest={}
for key,run_id,artifact_id,name,commit in sources:
    artifact=json.loads(subprocess.check_output(['gh','api',f'repos/{REPO}/actions/artifacts/{artifact_id}']))
    assert artifact['name']==name and not artifact['expired']
    assert artifact['workflow_run']['id']==run_id and artifact['workflow_run']['head_sha']==commit
    run(['gh','run','download',str(run_id),'-R',REPO,'-n',name,'-D',str(ROOT/key)])
    manifest[key]={'run':run_id,'artifact':artifact_id,'commit':commit,'artifact_digest':artifact.get('digest')}
for key in ['composition','journeys','accessibility','focus']:
    root=ROOT/key/'ci-out'
    assert (root/f'complete-{key}.txt').exists()
    assert not (root/'crashes.txt').read_text().strip()
    manifest[key]['screens']=len(list((root/'screens').glob('*.png')))
    dest=Path('evidence')/key
    shutil.copytree(root/'screens',dest/'screens',dirs_exist_ok=True)
    for f in list(root.glob('*passed.json'))+list(root.glob('*.mp4')):
        shutil.copy2(f,dest/f.name)
assert sum(manifest[k]['screens'] for k in ['composition','journeys','accessibility','focus'])==134
font=Path('/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf')
if not font.exists():
    run(['sudo','apt-get','update','-qq'])
    run(['sudo','apt-get','install','-y','fonts-dejavu-core'])
bold=font.with_name('DejaVuSans-Bold.ttf')
OLD=ROOT/'before/ci-out/screens';NEW=ROOT/'composition/ci-out/screens';J=ROOT/'journeys/ci-out/screens'

def board(filename,title,items):
    width=420;gap=28;pad=40;sh=747
    im=Image.new('RGB',(1844,1087),'#f4efe5');d=ImageDraw.Draw(im)
    d.text((pad,30),'ZikrTime · Живой горизонт · 0.8.11',font=ImageFont.truetype(str(font),25),fill='#586b60')
    d.text((pad,78),title,font=ImageFont.truetype(str(bold),36),fill='#153e34')
    d.text((pad,132),'Реальные снимки Android 15 / API 35. Интерфейс на снимках не ретуширован.',font=ImageFont.truetype(str(font),20),fill='#6a6459')
    assert len(items)==4
    for i,(path,label) in enumerate(items):
        img=Image.open(path).convert('RGB');img.thumbnail((width,sh),Image.Resampling.LANCZOS)
        x=pad+i*(width+gap);y=200
        im.paste(img,(x+(width-img.width)//2,y))
        d.text((x,y+sh+18),label,font=ImageFont.truetype(str(font),22),fill='#153e34')
    im.save(OUT/filename,quality=94)

labels={'knowledge':'Знания','focus':'Фокус','more':'Ещё','shop':'Магазин'}
for name,title,routes in [
    ('knowledge-focus.jpg','Знания и Фокус: было → стало',['knowledge','focus']),
    ('more-shop.jpg','Ещё и Магазин: было → стало',['more','shop'])]:
    items=[]
    for r in routes:items.extend([(OLD/(r+'-no.png'),labels[r]+' · 0.8.10'),(NEW/(r+'-light.png'),labels[r]+' · 0.8.11')])
    board(name,title,items)
board('night.jpg','Новая композиция в тёмной теме',[(NEW/(r+'-dark.png'),labels[r]) for r in ['knowledge','focus','more','shop']])
board('scroll.jpg','Прокрутка, настройки и возвращение',[(NEW/n,l) for n,l in [
    ('knowledge-scroll-light.png','Темы и компактная шапка'),('focus-settings-light.png','Панель настроек Фокуса'),
    ('shop-search-retained.png','Сохранённый поиск'),('more-without-art-scroll.png','Композиция без иллюстрации')]])
board('large-text.jpg','Небольшой экран и текст 200%',[(NEW/(r+'-200-scroll.png'),labels[r]) for r in ['knowledge','focus','more','shop']])
board('first-run.jpg','Первый запуск: проверено повторно',[(J/n,l) for n,l in [
    ('first-welcome.png','Приветствие'),('first-language.png','Язык'),('first-city-search.png','Выбор города'),('first-finish.png','Завершение')]])
board('reading.jpg','Чтение и внутренние действия',[(J/n,l) for n,l in [
    ('reading-morning.png','Чтение азкаров'),('reading-options.png','Размер текста'),('learning-confirm.png','Подтверждение'),('shop-empty.png','Пустой поиск')]])
shutil.copy2(ROOT/'composition/ci-out/knowledge-0811-route.mp4',OUT/'reading-scroll.mp4')
shutil.copy2(ROOT/'journeys/ci-out/onboarding-route.mp4',OUT/'first-run.mp4')
for name in ['focus-200-scroll.png','home-regression.png','shop-search-retained.png']:
    shutil.copy2(NEW/name,OUT/name)
apk=ROOT/'build/outputs/apk/debug/app-debug.apk'
digest=hashlib.sha256(apk.read_bytes()).hexdigest()
assert digest=='e5f801a6b51a96d06b3911ff8ad546f433e5821f27d9f4d7913bf7c34f81cb72'
assert apk.stat().st_size==93607969
meta=json.loads((apk.parent/'output-metadata.json').read_text())
assert all(x['versionName']=='0.8.11' and x['versionCode']==23 for x in meta['elements'])
Path('preview-apk').mkdir(exist_ok=True)
shutil.copy2(apk,'preview-apk/ZikrTime-0.8.11-debug.apk')
Path('preview-apk/SHA256SUMS.txt').write_text(digest+'  ZikrTime-0.8.11-debug.apk\n')
manifest['apk_sha256']=digest
(OUT/'sources.json').write_text(json.dumps(manifest,ensure_ascii=False,indent=2))
Path('evidence/sources.json').write_text(json.dumps(manifest,ensure_ascii=False,indent=2))
run_url=f"https://github.com/{REPO}/actions/runs/{os.environ['GITHUB_RUN_ID']}"
text=f"""# ZikrTime 0.8.11 — реальные скриншоты и записи

Перестроены четыре вкладки: «Знания», «Фокус», «Ещё», «Магазин». Отдельно создана панель настроек Фокуса. Главная сохраняет утверждённую композицию.

В каждой паре ниже: **0.8.10 → 0.8.11**. Снимки работающего Android-приложения, без ретуши интерфейса.

![Знания и Фокус](knowledge-focus.jpg)
![Ещё и Магазин](more-shop.jpg)

## Тёмная тема и прокрутка

![Тёмная тема](night.jpg)
![Прокрутка и настройки](scroll.jpg)
![320 dp и текст 200%](large-text.jpg)

## Повторная проверка первого запуска и чтения

Эти экраны сохраняют доработки 0.8.10; они не объявляются заново переработанными в этой итерации. Снимки сделаны в полном прогоне 0.8.11 на коммите 675619b.

![Первый запуск](first-run.jpg)
![Чтение и вспомогательные состояния](reading.jpg)

## Записи

- [Прокрутка → чтение → возврат → тема, окончательная сборка](reading-scroll.mp4)
- [Чистый первый запуск → главная, полный прогон 675619b](first-run.mp4)

Исходные записи без ускорения и интерполяции. Они показывают реальные переходы; плавность на физическом телефоне ещё требует проверки.

## APK и доказательства

[Скачать артефакты этой сборки]({run_url}): **ZikrTime-0.8.11-APK** содержит один установочный APK и контрольную сумму; **ZikrTime-0.8.11-evidence** содержит 134 исходных кадра, записи и результаты проверок.

APK тестовый (debug), 0.8.11 / 23. SHA-256: **{digest}**. Совместимость подписи с установленной у пользователя версией не подтверждена; удалять приложение с личными данными ради установки не нужно.

- [Отчёт: изменения, проверки и ограничения](../composition-0811.md)
- [Карта 83 сценариев с отдельными статусами кода и проверки](../app-redesign-map.csv)
- [Точные версии и источники кадров](sources.json)

Android 15 / API 35. 54 unit-теста, 0 ошибок Lint. Полные journeys/focus/accessibility проверены на 675619b; после исправления отрисовки под системными часами build и composition повторены на c45fad4. Первые пять иллюстраций используют окончательные кадры c45fad4. Это не подтверждение iOS, аппаратных датчиков, реальных сигналов по расписанию или совместимости подписи с установленным APK.
"""
(OUT/'README.md').write_text(text)
print(json.dumps({'gallery':str(OUT),'screens':134,'apk_sha256':digest}))
