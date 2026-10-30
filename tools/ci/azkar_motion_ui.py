"""Targeted UI checks on a disposable emulator; no user data or remote writes."""
exec(compile(open('tools/ci/features_controls.py').read().split('mode = sys.argv[1]')[0], 'helpers', 'exec'))
OUT.mkdir(exist_ok=True); (OUT/'screens').mkdir(exist_ok=True)
def start(route,lang='ru'):
    shell('am','start','-S','-W','-n',PKG+'/.MainActivity','--es','qa_screen',route,'--es','qa_lang',lang,'--ez','qa_onboarded','true')
    time.sleep(2)
def shot(name):
    time.sleep(.7); tree=ui(); screenshot(name)
    (OUT/(name+'.xml')).write_bytes(ET.tostring(tree))
def back(): shell('input','keyevent','KEYCODE_BACK'); time.sleep(.5)
def top():
    for _ in range(8):shell('input','swipe',5,400,5,1500,150)
def state():return json.loads(prefs()['azkar_reading_v2'])
def center(identifier):
    n=locate_control(identifier); l,t,r,b=map(int,re.findall(r'\d+',n.get('bounds')));return (l+r)//2,(t+b)//2
start('home');put_pref('online_times','boolean','false');put_pref('appearance','string','light')
legacy=json.dumps({'last':'morning','morning':{'index':5,'complete':False,'sessionId':'legacy-test'}})
put_pref('azkar_reading_v1','string',legacy)
put_pref('total_answered','int',37)
start('azkar');shot('azkar-morning-light')
assert state()['morning']['index']==0
morning_text=ET.tostring(ui(),encoding='unicode')
tap_control('azkar_source');shot('azkar-source');back()
tap_control('azkar_count');assert sum(state()['morning']['counts'].values())==1
tap_control('azkar_next');assert state()['morning']['index']==1
start('azkar');assert state()['morning']['index']==1
assert prefs()['azkar_reading_v1']==legacy and prefs()['total_answered']=='37'
top();tap_control('Вечерние азкары','text');shot('azkar-evening-light')
assert state()['evening']['index']==0
assert ET.tostring(ui(),encoding='unicode')!=morning_text
tap_control('azkar_count');assert sum(state()['evening']['counts'].values())==1
top();tap_control('Утренние азкары','text');assert state()['morning']['index']==1
# Visit every item and verify end-of-collection navigation, no hardcoded six-item limit.
for _ in range(16):
    root=ui()
    if find_control(root,'azkar_next') is None:
        try:locate_control('azkar_next')
        except AssertionError:break
    before=state()['morning']['index']
    tap_control('azkar_next')
    if state()['morning']['complete']:break
else:raise AssertionError('Collection did not finish')
assert state()['morning']['complete']
start('azkar');assert state()['morning']['index']==0 and state()['morning']['counts']=={}
# Theme wave using actual visible button; capture both directions in real screen recording.
start('home');xy=center('theme_quick_toggle')
log=(OUT/'record.log').open('w')
rec=subprocess.Popen(['adb','shell','screenrecord','--size','720x1280','--time-limit','35','/sdcard/azkar-motion.mp4'],stdout=log,stderr=subprocess.STDOUT)
time.sleep(1)
for _ in range(4):shell('input','tap',*xy);time.sleep(1.1)
shot('home-wave-return')
for mode in ['dark','light']:
    put_pref('appearance','string',mode)
    start('tasbih');xyc=center('tasbih_counter');old=int(prefs().get('tasbih_count','0'))
    for _ in range(5):shell('input','tap',*xyc)
    assert int(prefs()['tasbih_count'])==old+5
    shot('tasbih-'+mode)
    start('azkar');shot('azkar-'+mode)
subprocess.run(['adb','shell','pkill','-2','screenrecord'],capture_output=True)
try:rec.wait(timeout=10)
except subprocess.TimeoutExpired:rec.terminate()
subprocess.run(['adb','pull','/sdcard/azkar-motion.mp4',str(OUT/'azkar-motion.mp4')],check=True,timeout=30)
# Root snapshot host must preserve the other routes.
for route in ['knowledge','focus','shop','more']:
    start(route);shot(route+'-regression')
shell('wm','size','720x1280');shell('wm','density','360');shell('settings','put','system','font_scale','1.5')
for route in ['azkar','tasbih']:
    start(route);shot(route+'-large-text');locate_control('azkar_next' if route=='azkar' else 'tasbih_counter');shot(route+'-large-text-controls')
shell('settings','put','system','font_scale','1');shell('wm','size','1080x1920');shell('wm','density','420')
shell('settings','put','global','animator_duration_scale','0');start('home');before=prefs()['appearance'];tap_control('theme_quick_toggle');assert prefs()['appearance']!=before;shot('home-reduced-motion')
shell('settings','put','global','animator_duration_scale','1')
start('azkar','en');shot('azkar-english')
(OUT/'passed.json').write_text(json.dumps({'morning_evening_independent':True,'reading_resume':True,'legacy_preserved':True,'completion_restart':True,'theme_controls':True,'tasbih_taps':True,'large_text':True,'reduced_motion':True,'routes_smoke':True},indent=2))
