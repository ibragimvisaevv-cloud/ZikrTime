"""Preserve real launcher evidence and editable icon concepts, without changing app code."""
import json,subprocess,shutil,io,zipfile
from pathlib import Path
from PIL import Image,ImageDraw,ImageFont
import cairosvg
RUN=41030076563
SHA="ab898fe0c1b6644e4aa20e8d7d94c3ee15396811"
repo="ibragimvisaevv-cloud/ZikrTime"
run=json.loads(subprocess.check_output(["gh","api",f"repos/{repo}/actions/runs/{RUN}"],text=True))
assert run["head_sha"]==SHA
subprocess.run(["gh","run","download",str(RUN),"-n","Android-widgets-launcher","-D","widget-evidence"],check=True)
evidence=Path("widget-evidence")
# Each group keeps exact provenance; appearance and data checks were independent runs.
sources={"lifecycle":{"run":RUN,"commit":SHA,"app_commit":"b1873848de9c09b226c30ed5a7ef2090746c2f99"},
         "actions":{"run":41028419737,"commit":"77473b1119f037dbb86fdfc1c69528b6126adc9e","app_commit":"b1873848de9c09b226c30ed5a7ef2090746c2f99"},
         "appearance":{"run":41025914614,"commit":"b1873848de9c09b226c30ed5a7ef2090746c2f99","app_commit":"b1873848de9c09b226c30ed5a7ef2090746c2f99"},
         "data":{"run":41025421561,"commit":"f0c8d585fd27e4de5c8657430f371f4b74f8913a","app_commit":"e06eeac24b1c806d2242fcdbecc56f1d63a25411"}}
for group in ["appearance","data","actions"]:
    spec=sources[group]
    actual=json.loads(subprocess.check_output(["gh","api",f"repos/{repo}/actions/runs/{spec['run']}"],text=True))
    assert actual['head_sha']==spec['commit']
    subprocess.run(["gh","run","download",str(spec['run']),"-n","Android-widgets-launcher","-D",str(evidence/group)],check=True)
appearance=evidence/"appearance"
out=Path("design/widgets-0812/review");shutil.rmtree(out,ignore_errors=True);out.mkdir(parents=True,exist_ok=True)
def font(n,b=False):return ImageFont.truetype("/usr/share/fonts/truetype/dejavu/DejaVuSans"+("-Bold" if b else "")+".ttf",n)
def board(name,items,title):
    page=Image.new("RGB",(960,1770),"#EEECE2");d=ImageDraw.Draw(page)
    d.text((28,24),title,font=font(25,True),fill="#173F34")
    for i,(file,label) in enumerate(items):
        p=appearance/"screens"/(file+".png")
        if not p.exists():continue
        im=Image.open(p).convert("RGB");im.thumbnail((430,780))
        x=28+(i%2)*480;y=100+(i//2)*820
        d.text((x,y-30),label,font=font(17),fill="#314B3E");page.paste(im,(x,y))
    page.save(out/name,quality=90)
board("launcher-light.jpg",[("A-compact-light","A · Ближайший намаз"),("B-timeline-light","B · Линия времени"),("C-square-light","C · Мой намаз"),("D-day-light","D · Расписание дня")],"ZikrTime · реальные снимки Android 15")
board("launcher-dark.jpg",[("A-compact-dark","A · тёмное оформление"),("B-timeline-dark","B · тёмное оформление"),("C-square-dark","C · тёмное оформление"),("D-day-dark","D · тёмное оформление")],"Темы и дополнительные виджеты")
for name in [p.name for p in evidence.iterdir() if p.is_file() and p.suffix in [".json",".xml",".txt",".mp4"]]:
    if (evidence/name).exists():shutil.copy2(evidence/name,out/name)
# Separate original screenshots are convenient for close inspection (JPEG compression only).
for p in (evidence/"screens").glob("*.png"):
    Image.open(p).convert("RGB").save(out/(p.stem+".jpg"),quality=91)
for group in ["appearance","data","actions"]:
    folder=out/group;folder.mkdir()
    for p in (evidence/group).iterdir():
        if p.is_file() and p.suffix in [".json",".txt",".mp4"]:shutil.copy2(p,folder/p.name)
    for p in (evidence/group/"screens").glob("*.png"):
        Image.open(p).convert("RGB").save(folder/(p.stem+".jpg"),quality=91)
shutil.copy2(evidence/"actions"/"widgets.mp4",out/"widgets.mp4")
(out/"provenance.json").write_text(json.dumps(sources,indent=2))
(out/"source.json").write_text(json.dumps({"run":RUN,"commit":SHA,"conclusion":run["conclusion"]},indent=2))
icons=Path("design/widgets-0812/icons")
def render(path,n=1024):
    return Image.open(io.BytesIO(cairosvg.svg2png(url=str(path),output_width=n,output_height=n))).convert("RGBA")
def masked(im,n,shape="round"):
    im=im.resize((int(n*1.5),int(n*1.5)),Image.Resampling.LANCZOS);k=(im.width-n)//2;im=im.crop((k,k,k+n,k+n))
    mask=Image.new("L",(n,n));d=ImageDraw.Draw(mask)
    if shape=="circle":d.ellipse((0,0,n-1,n-1),fill=255)
    else:d.rounded_rectangle((0,0,n-1,n-1),n*(.24 if shape=="round" else .14),fill=255)
    im.putalpha(mask);return im
def generic(n,col,kind):
    im=Image.new("RGBA",(n,n));d=ImageDraw.Draw(im);d.rounded_rectangle((0,0,n-1,n-1),n*.24,fill=col)
    if kind==0:d.arc((n*.24,n*.2,n*.82,n*.75),40,220,fill="white",width=int(n*.13))
    elif kind==1:
        d.rounded_rectangle((n*.19,n*.29,n*.81,n*.75),4,outline="white",width=2);d.ellipse((n*.37,n*.37,n*.63,n*.64),outline="white",width=2)
    elif kind==2:
        d.ellipse((n*.25,n*.25,n*.75,n*.75),outline="white",width=3);d.line((n*.5,n*.27,n*.5,n*.51,n*.64,n*.58),fill="white",width=2)
    else:
        d.rectangle((n*.22,n*.23,n*.78,n*.76),outline="white",width=2);d.line((n*.22,n*.36,n*.78,n*.36),fill="white",width=2)
    return im
def desktop(icon,dark):
    im=Image.new("RGB",(340,530));d=ImageDraw.Draw(im)
    a=(20,47,42) if dark else (226,230,216);b=(64,81,64) if dark else (185,204,188)
    for y in range(530):
        t=y/530;d.line((0,y,340,y),fill=tuple(round(a[k]*(1-t)+b[k]*t) for k in range(3)))
    fg="#F8F3E9" if dark else "#213E33"
    d.text((24,20),"9:41",font=font(12,True),fill=fg);d.text((24,70),"Пятница",font=font(17),fill=fg);d.text((24,99),"30 октября",font=font(28),fill=fg)
    d.arc((-70,210,440,790),190,320,fill="#647F6C" if dark else "#95AF9C",width=3)
    labels=["Телефон","Камера","ZikrTime","Календарь"]
    for i in range(4):
        x=25+i*78;item=masked(icon,52) if i==2 else generic(52,["#42765D","#697973","#35695B","#60756D"][i],i)
        im.paste(item,(x,270),item);d.text((x,332),labels[i],font=font(10),fill=fg)
        item=generic(48,["#48796D","#8D967B","#B19668","#53716D"][i],i);im.paste(item,(28+i*78,438),item)
    d.rounded_rectangle((130,511,210,515),2,fill=fg)
    return im
for concept in json.loads((icons/"concepts.json").read_text()):
    slug=concept["slug"];folder=icons/slug;icon=render(folder/"icon.svg");icon.save(folder/"icon-1024.png")
    page=Image.new("RGB",(1500,1060),"#F3F0E7");d=ImageDraw.Draw(page)
    d.text((54,42),"ZIKRTIME  /  ЖИВОЙ ГОРИЗОНТ",font=font(15,True),fill="#63766B")
    d.text((54,82),concept["title"],font=font(42,True),fill="#193D33");d.text((54,143),concept["desc"],font=font(18),fill="#526256")
    big=masked(icon,260);page.paste(big,(54,210),big);d.text((54,492),"Основной знак",font=font(16),fill="#536358")
    d.rounded_rectangle((350,210,650,510),24,fill="#172F29");big=masked(icon,190,"circle");page.paste(big,(405,252),big)
    for n,x in [(24,54),(32,154),(48,270)]:
        d.text((x,562),str(n)+" px",font=font(15),fill="#536358");im=masked(icon,n);page.paste(im,(x,600),im)
    d.text((54,693),"Формы маски Android",font=font(18,True),fill="#193D33")
    for shape,x in [("circle",54),("round",180),("square",306)]:
        im=masked(icon,92,shape);page.paste(im,(x,736),im)
    d.text((54,873),"Тематический значок",font=font(18,True),fill="#193D33")
    mono=(folder/"monochrome.svg").read_text()
    for x,col,bg in [(54,"#204E3D","#C7D9C6"),(180,"#D8E5CB","#234235")]:
        im=Image.open(io.BytesIO(cairosvg.svg2png(bytestring=mono.replace("#FFFFFF",col).encode(),output_width=144,output_height=144))).convert("RGBA")
        b=Image.new("RGBA",(144,144),bg);b.alpha_composite(im);b=masked(b,82);page.paste(b,(x,913),b)
    for x,dark in [(740,False),(1120,True)]:
        page.paste(desktop(icon,dark),(x,240));d.text((x,795),"Тёмные обои" if dark else "Светлые обои",font=font(16),fill="#536358")
    d.text((740,868),"Дизайнерский макет рабочего стола",font=font(15),fill="#536358")
    page.save(icons/(slug+"-preview.jpg"),quality=92)
with zipfile.ZipFile(out/"ZikrTime-icon-concepts.zip","w",zipfile.ZIP_DEFLATED) as z:
    for p in icons.rglob("*"):
        if p.is_file():z.write(p,p.relative_to(icons))
with zipfile.ZipFile(out/"ZikrTime-approved-dome-icon.zip","w",zipfile.ZIP_DEFLATED) as z:
    for p in (icons/"02-dome").iterdir():
        if p.is_file():z.write(p,p.name)
    z.writestr("README.txt","Approved by the user: Купол и горизонт, 2026-10-30.\nAndroid application layers use these VectorDrawable sources. SVG files remain editable.\n")
(out/"README.md").write_text(f"# Виджеты ZikrTime — проверка\n\nРеальные снимки Android 15 / API 35, stock launcher, 1080×1920, 420 dpi.\n\nКод приложения: b1873848de9c09b226c30ed5a7ef2090746c2f99; версия проверочного сценария: {SHA}; [прогон {RUN}](https://github.com/{repo}/actions/runs/{RUN}), статус: {run['conclusion']}.\n\n![Светлые](launcher-light.jpg)\n\n![Тёмные](launcher-dark.jpg)\n\n[Запись](widgets.mp4). [Иконки и исходники](ZikrTime-icon-concepts.zip).\n\nОформление, данные и оставшиеся действия проверялись отдельными прогонами. Точные исходные сборки указаны в provenance.json. Журнал основных проверок данных: data/widget-checks.json; нажатия и тасбих: actions/widget-checks.json; изменение размера, обновление и перезагрузка: widget-checks.json. Запись перехода к следующему намазу: data/prayer-boundary.mp4. Общая сводка находится в ../README.md. Утверждённый «Купол и горизонт» внедрён в APK. Концептуальные макеты находятся в ../icons; фотографии рабочего стола выше получены в эмуляторе.\n")
