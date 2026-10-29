"""Place real AppWidgets in the stock launcher. Never substitute in-app preview screenshots."""
exec(compile(open('tools/ci/features_controls.py').read().split('mode = sys.argv[1]')[0], 'helpers', 'exec'))
OUT.mkdir(exist_ok=True);(OUT/'screens').mkdir(exist_ok=True)
checks=[]
def check(name,value):
    checks.append({'name':name,'passed':bool(value)})
    (OUT/'widget-checks.json').write_text(json.dumps(checks,indent=2))
    assert value,name

def probe(action,provider='CompactPrayerWidget',**kwargs):
    args=['am','start','-W','-n',PKG+'/.widget.WidgetProbeActivity','--es','action',action,'--es','provider',provider]
    for k,v in kwargs.items():args+=['--es',k,str(v)]
    shell(*args);time.sleep(2)

def home():
    resumed=[l for l in shell('dumpsys','activity','activities').splitlines() if 'mResumedActivity' in l]
    if not any('launcher' in l.lower() for l in resumed):shell('input','keyevent','KEYCODE_HOME')
    time.sleep(2)
def capture(name):time.sleep(2);screenshot(name)
def pin(provider):
    probe('pin',provider);capture('pin-'+provider)
    root=ui();(OUT/('pin-'+provider+'.xml')).write_bytes(ET.tostring(root))
    candidates=[n for n in root.iter('node') if n.get('text','').lower() in ['add to home screen','add automatically','add','добавить','добавить автоматически']]
    if not candidates:candidates=[n for n in root.iter('node') if n.get('resource-id','').endswith('/add_button')]
    check('pin confirmation '+provider,bool(candidates))
    n=candidates[-1];l,t,r,b=map(int,re.findall(r'\d+',n.get('bounds')));shell('input','tap',(l+r)//2,(t+b)//2);time.sleep(3)
    home();capture(provider+'-default')
    probe('inspect',provider)
    data=json.loads(adb('exec-out','run-as',PKG,'cat','files/widget-probe.json'))
    (OUT/(provider+'-bound.json')).write_text(json.dumps(data,indent=2))
    check('bound in real launcher '+provider,any(w['provider']==provider for w in data['widgets']))
    home()

shell('pm','clear',PKG)
home();pin('CompactPrayerWidget');capture('empty-city')
shell('am','start','-S','-W','-n',PKG+'/.MainActivity','--es','qa_screen','home','--es','qa_lang','ru','--ez','qa_onboarded','true')
time.sleep(3)
put_pref('online_times','boolean','false');put_pref('appearance','string','light')
probe('refresh');home();capture('A-compact-light')
for provider,label in [('TimelineWidget','B-timeline'),('NextPrayerWidget','C-square'),('TodayWidget','D-day'),('TasbihWidget','E-tasbih'),('ScreenTimeWidget','F-focus')]:
    pin(provider);capture(label+'-light')
    probe('theme',provider,theme='dark');home();capture(label+'-dark')
    probe('theme',provider,theme='app')
probe('refresh');home()
# A real native Chronometer changes while ZikrTime has no foreground Activity.
rec=subprocess.Popen(['adb','shell','screenrecord','--size','720x1280','--bit-rate','1800000','--time-limit','14','/sdcard/widgets.mp4'])
time.sleep(14);rec.wait(timeout=15)
subprocess.run(['adb','pull','/sdcard/widgets.mp4',str(OUT/'widgets.mp4')],check=True)
(OUT/'completed.txt').write_text('Launcher placement and themes completed. Additional scenarios require their own evidence.\n')
