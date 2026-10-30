"""Additional real launcher interactions on a disposable, signed-test emulator profile."""
import datetime as dt
def data():
    probe('inspect')
    return json.loads(adb('exec-out','run-as',PKG,'cat','files/widget-probe.json'))
def rendered(widget_id):
    return json.loads(adb('exec-out','run-as',PKG,'cat',f'files/widget-render-{widget_id}.json'))
def shown():
    root=ui();return [n for n in root.iter('node') if n.get('resource-id','').endswith('/w_countdown')]
def first_page():
    show_provider('CompactPrayerWidget')
def record_start(name):
    return subprocess.Popen(['adb','shell','screenrecord','--size','720x1280','--bit-rate','1800000','--time-limit','35','/sdcard/'+name+'.mp4'])
def record_end(proc,name):
    subprocess.run(['adb','shell','pkill','-2','screenrecord'],stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL)
    proc.wait(timeout=15)
    subprocess.run(['adb','pull','/sdcard/'+name+'.mp4',str(OUT/(name+'.mp4'))],check=True)
def parse_time(value):return dt.datetime.fromisoformat(value.split('[')[0])
def set_time(value):
    shell('settings','put','global','auto_time',0)
    shell('cmd','alarm','set-time',int(value.timestamp()*1000));time.sleep(2)
def all_pages(name):
    first_page()
    for page in range(3):
        capture(name+'-'+str(page))
        page_swipe()

state=data();ids={w['provider']:w['id'] for w in state['widgets']};first_page()
# Native clock changes without an app Activity or ticking app process.
before=[n.get('text') for n in shown()];shell('am','kill',PKG);time.sleep(3)
after=[n.get('text') for n in shown()]
check('native countdown changes while app is in background',bool(before) and before!=after)
capture('native-countdown-background')
# Actual registered clock-change and alarm receivers, using calculated prayer times.
state=data();first_page();at=parse_time(state['at']);set_time(at-dt.timedelta(seconds=15))
old=rendered(ids['CompactPrayerWidget'])
rec=record_start('prayer-boundary');capture('before-prayer-boundary');time.sleep(20);capture('after-prayer-boundary');record_end(rec,'prayer-boundary')
new=rendered(ids['CompactPrayerWidget'])
check('next prayer changes after countdown expires',new['at']!=old['at'] and parse_time(new['at'])>at)
snapshots=[rendered(ids[p]) for p in ['CompactPrayerWidget','TimelineWidget','NextPrayerWidget','TodayWidget']]
check('all four widgets agree after prayer boundary',len({(s['next'],s['at']) for s in snapshots})==1)
(OUT/'boundary-state.json').write_text(json.dumps({'before':old,'after':new,'all':snapshots},indent=2))
state=data();isha=parse_time(state['isha']);set_time(isha+dt.timedelta(seconds=2));time.sleep(2)
n=rendered(ids['CompactPrayerWidget'])
check('after Isha selects next day Fajr',n['next']=='FAJR' and parse_time(n['at']).date()>isha.date())
capture('after-isha')
midnight=(isha+dt.timedelta(days=1)).replace(hour=0,minute=0,second=0,microsecond=0)
set_time(midnight-dt.timedelta(seconds=14));capture('before-midnight');time.sleep(18);capture('after-midnight')
n=rendered(ids['TodayWidget'])
check('date updates at midnight without opening app',n['date']==midnight.date().isoformat())
# City updates go through Store and the debounced listener, not an explicit renderer call.
probe('city',city='saint_petersburg');time.sleep(2)
check('city listener refreshes placed widget',rendered(ids['TimelineWidget'])['city']=='saint_petersburg')
all_pages('long-city')
probe('city',city='moscow');probe('inspect');state=data();first_page()
# Pick the same day's Dhuhr so a +15 minute calculation adjustment must change the visible next time.
set_time(parse_time(state['now']).replace(hour=10,minute=0,second=0,microsecond=0));time.sleep(2)
before=rendered(ids['CompactPrayerWidget']);probe('adjust',minutes=15);time.sleep(2);after=rendered(ids['CompactPrayerWidget'])
check('calculation adjustment updates widgets',parse_time(after['at'])-parse_time(before['at'])==dt.timedelta(minutes=15))
probe('adjust',minutes=0)
shell('cmd','alarm','set-timezone','Asia/Tashkent');time.sleep(3)
check('selected city timezone remains Moscow',parse_time(rendered(ids['CompactPrayerWidget'])['at']).utcoffset()==dt.timedelta(hours=3))
# Real per-widget settings screen, followed by a saved per-instance theme.
shell('am','start','-W','-n',PKG+'/.widget.WidgetConfigActivity','--ei','appWidgetId',ids['NextPrayerWidget']);time.sleep(2);capture('widget-settings')
tap_control('Тёмная','text');tap_control('Готово','text');time.sleep(2)
check('per-instance theme saved',prefs('widget_appearance').get('theme_'+str(ids['NextPrayerWidget']))=='dark')
check('per-instance dark appearance rendered',rendered(ids['NextPrayerWidget'])['dark'])
all_pages('mixed-themes')
probe('theme','CompactPrayerWidget',theme='system')
shell('cmd','uimode','night','yes');time.sleep(2);first_page();capture('system-theme-dark')
shell('cmd','uimode','night','no');time.sleep(2);first_page();capture('system-theme-light')
probe('theme','CompactPrayerWidget',theme='app')
# Wallpaper readability: actual system wallpaper changes, no screenshot compositing.
for background in ['light','dark','busy']:
    probe('wallpaper',theme=background);all_pages('wallpaper-'+background)
# Large text and narrow phone: real system configuration changes and real host reflow.
shell('settings','put','system','font_scale','2.0');time.sleep(3);probe('refresh');all_pages('font-200')
shell('settings','put','system','font_scale','1.0');shell('wm','size','720x1280');shell('wm','density','320');time.sleep(3);probe('refresh')
all_pages('small-phone')
shell('wm','size','1080x1920');shell('wm','density','420');time.sleep(3);probe('refresh')
# Tap opens prayer tab, including when coming from another in-app screen.
first_page()
root=ui();node=find_control(root,'w_time')
check('visible prayer time tap target',node is not None)
l,t,r,b=map(int,re.findall(r'\d+',node.get('bounds')));shell('input','tap',(l+r)//2,(t+b)//2);time.sleep(2);capture('widget-opens-prayer')
opened=ui()
check('widget opens prayer screen',next(opened.iter('node')).get('package')==PKG and any(n.get('text')=='Намазы сегодня' for n in opened.iter('node')))

# Preserved tasbih actions and focus deep link.
show_provider('TasbihWidget');old_count=int(prefs().get('tasbih_count','0'));tap_control('w_count')
check('tasbih widget increments shared counter',int(prefs().get('tasbih_count','0'))==old_count+1)
tap_control('w_reset');check('tasbih reset preserved',int(prefs().get('tasbih_count','0'))==0)
show_provider('ScreenTimeWidget');tap_control('w_value');capture('widget-opens-focus')
shell('input','keyevent','KEYCODE_HOME')
# Real launcher resize gesture, with before/after options from the bound provider.
width_before=next(w['minWidth'] for w in data()['widgets'] if w['provider']=='CompactPrayerWidget')
resize_states=[]
for attempt,offset in enumerate([12,-12,4]):
    root=show_provider('CompactPrayerWidget')
    node=next(node for node in root.iter('node') if '2×1' in node.get('content-desc',''))
    l,t,r,b=map(int,re.findall(r'\d+',node.get('bounds')))
    shell('input','swipe',(l+r)//2,(t+b)//2,(l+r)//2,(t+b)//2,1000);time.sleep(1)
    handles=ui();(OUT/f'resize-ui-{attempt}.xml').write_bytes(ET.tostring(handles))
    capture('resize-handles-'+str(attempt))
    # Pixel Launcher draws its handle just outside the widget content bounds.
    shell('input','swipe',r+offset,(t+b)//2,824,(t+b)//2,1200);time.sleep(2)
    capture('resize-drag-'+str(attempt))
    shell('input','tap',950,1250);time.sleep(1);capture('resized-wide-'+str(attempt))
    resized=data();resize_states.append(resized)
    width_after=next(w['minWidth'] for w in resized['widgets'] if w['provider']=='CompactPrayerWidget')
    if width_after>width_before+40:break
(OUT/'resize-states.json').write_text(json.dumps(resize_states,indent=2))
# Continue independent upgrade/reboot checks even if the launcher gesture failed.
checks.extend([{'name':'launcher resize changes bound widget width','passed':width_after>width_before+40},
               {'name':'resized compact reflows to horizontal composition','passed':rendered(ids['CompactPrayerWidget'])['layout']=='WIDE'}])
(OUT/'widget-checks.json').write_text(json.dumps(checks,indent=2))

# Update already placed legacy providers with identical disposable signing key.
state=data();legacy={p:ids[p] for p in ['NextPrayerWidget','TodayWidget','TasbihWidget','ScreenTimeWidget']};saved=prefs()
subprocess.run(['adb','install','-r','-d','fixtures/previous.apk'],check=True);time.sleep(3)
shell('am','start','-W','-n',PKG+'/.MainActivity');time.sleep(3);shell('input','keyevent','KEYCODE_HOME');capture('old-version-with-placed-widgets')
subprocess.run(['adb','install','-r','fixtures/current.apk'],check=True);time.sleep(6)
state=data();now_ids={w['provider']:w['id'] for w in state['widgets']}
check('upgrade preserves four legacy provider IDs',all(now_ids.get(k)==v for k,v in legacy.items()))
current=prefs();keys=['city_id','lat','lon','method','madhab','appearance','onboarded']
check('upgrade preserves existing settings',all(saved.get(k)==current.get(k) for k in keys))
show_provider('TodayWidget');capture('upgraded-widgets')
# Normal reboot; do not launch the app before the screenshot/state check.
adb('reboot');adb('wait-for-device')
deadline=time.monotonic()+180
while shell('getprop','sys.boot_completed').strip()!='1':
    if time.monotonic()>deadline:raise AssertionError('Emulator did not reboot')
    time.sleep(2)
shell('input','keyevent','KEYCODE_WAKEUP');shell('wm','dismiss-keyguard');time.sleep(8)
capture('widgets-after-reboot')
check('reboot refreshes existing schedule widget',parse_time(rendered(ids['TodayWidget'])['now'])>=parse_time(n['now']))
(OUT/'events-completed.txt').write_text('Executed scope: '+scope+'. See widget-checks.json for individual outcomes.\n')
assert all(item['passed'] for item in checks),'One or more launcher checks failed; see widget-checks.json'
