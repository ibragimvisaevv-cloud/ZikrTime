"""Real UI evidence on disposable profiles. No reviews, messages, or calls are sent."""
import io
from PIL import Image
exec(compile(open('tools/ci/features_controls.py').read().split('mode = sys.argv[1]')[0], 'features_helpers', 'exec'))

GROUP = sys.argv[1]
OUT.mkdir(exist_ok=True); (OUT/'screens').mkdir(exist_ok=True)
records=[]
RU={n.attrib['name']:n.text or '' for n in ET.parse('app/src/main/res/values-ru/strings.xml').getroot() if n.tag=='string'}

def start(screen, lang='ru', onboarded=True):
    shell('am','start','-S','-W','-n',PKG+'/.MainActivity','--es','qa_screen',screen,'--es','qa_lang',lang,
          '--ez','qa_onboarded',str(onboarded).lower())
    time.sleep(2)

def shot(name, ids=(), hierarchy=False):
    screenshot(name)
    if hierarchy: (OUT/(name+'.xml')).write_bytes(ET.tostring(ui()))
    records.append({'image':name+'.png','scenarios':list(ids),'captured':True,'visually_reviewed':False})
    (OUT/'capture-index.json').write_text(json.dumps(records,ensure_ascii=False,indent=2))

def label(key): return RU[key]
def tap(key): tap_control(label(key),'text')
def back(): shell('input','keyevent','KEYCODE_BACK'); time.sleep(.8)
def swipe(up=True, times=1):
    png=subprocess.check_output(['adb','exec-out','screencap','-p'],timeout=30)
    w,h=Image.open(io.BytesIO(png)).size
    for _ in range(times):
        shell('input','swipe',5,int(h*(.72 if up else .27)),5,int(h*(.27 if up else .72)),450)
    time.sleep(.5)
def type_query(text):
    tap_control('android.widget.EditText','class'); shell('input','text',text); time.sleep(.6)
def theme(value):
    put_pref('appearance','string',value)
def record(name):
    return subprocess.Popen(['adb','shell','screenrecord','--size','720x1280','--bit-rate','1500000','--time-limit','120','/sdcard/'+name+'.mp4'],stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL)
def finish_record(proc,name):
    # Signal the device encoder so the original video gets a complete container.
    shell('pkill','-2','screenrecord')
    try: proc.wait(timeout=15)
    except subprocess.TimeoutExpired: proc.terminate()
    adb('pull','/sdcard/'+name+'.mp4',str(OUT/(name+'.mp4')))

if GROUP=='tour':
    start('home'); theme('system')
    routes=['home','knowledge','shop','focus','more','language','city','prayer_settings','alarm','qibla','tasbih',
            'about','apps','review','progress','azkar','quiz','quiz_cards','shop_seller','shop_product','places','ramadan','zakat','widgets','block_limit','block_prayer','alarm_ring']
    mapping={'home':['home'],'quiz':['quiz_intro'],'quiz_cards':['study'],'review':['review_empty'],
        'prayer_settings':['prayer_settings'],'alarm':['alarm'],'azkar':['azkar_morning'],'shop_seller':['seller'],
        'shop_product':['product'],'places':['places','places_status'],'ramadan':['ramadan'],'zakat':['zakat'],
        'qibla':['qibla','qibla_sensor'],'focus':['focus'],'more':['more'],'language':['language'],
        'progress':['progress'],'widgets':['widgets'],'block_limit':['block_limit'],'block_prayer':['block_prayer'],'alarm_ring':['alarm_ring']}
    # Actual catalog and existing state only; synthetic demo content is not substituted.
    for mode in ['no','yes']:
        shell('cmd','uimode','night',mode)
        for route in routes:
            start(route)
            shot(route+'-'+mode,mapping.get(route,[route]))
            if route in ['prayer_settings','alarm','focus','more','about','zakat','places','ramadan','shop_seller']:
                swipe(times=8)
                shot(route+'-bottom-'+mode,{
                    'prayer_settings':['prayer_adjustments','prayer_notifications','prayer_source'],
                    'alarm':['alarm_options','focus_permissions'],'focus':['focus_test','focus_permissions'],
                    'more':['donate'],'about':['about'],'zakat':['zakat_prices'],
                    'places':['places_status'],'ramadan':['ramadan_settings'],'shop_seller':['reviews','shop_contacts']}.get(route,[]))
    shell('cmd','uimode','night','no')

elif GROUP=='journeys':
    # Real clean first run; preserve a complete recording of the route to Home.
    shell('pm','clear',PKG)
    start('onboarding',onboarded=False)
    proc=record('onboarding-route')
    shot('first-welcome',['welcome','clean'])
    tap_control('onboarding_next')
    shot('first-language',['onboarding_language'])
    tap_control('onboarding_next')
    shot('first-city',['onboarding_city'])
    type_query('Moscow'); back()
    shot('first-city-search',['onboarding_city'])
    tap_control('Москва','text')
    shot('first-permissions',['onboarding_perms'])
    tap_control('onboarding_next')
    shot('first-finish',['onboarding_finish'])
    tap_control('onboarding_next')
    shot('first-home',['clean','home'],True)
    assert prefs()['onboarded']=='true'
    finish_record(proc,'onboarding-route')
    # Themes persist independently of the device setting.
    subprocess.run([sys.executable,'tools/ci/features_controls.py','appearance'],check=True)
    theme('light')
    start('knowledge')
    proc=record('reading-route')
    tap('azkar_morning');shot('reading-morning',['azkar_morning'])
    tap_control('reader_options');shot('reading-options',['reader_options'],True)
    node=locate_control('reader_scale');l,t,r,b=map(int,re.findall(r'\d+',node.attrib['bounds']))
    shell('input','tap',int(l+(r-l)*.75),(t+b)//2);tap('action_done')
    shot('reading-enlarged',['reader_options'])
    tap('azkar_next');shot('reading-next',['azkar_navigation'])
    assert json.loads(prefs()['azkar_reading_v1'])['morning']['index']==1
    back();shot('knowledge-return',['knowledge','navigation'])
    finish_record(proc,'reading-route')
    start('azkar');tap('azkar_evening');shot('reading-evening',['azkar_evening'])
    start('tasbih');tap('action_reset');shot('counter-confirm',['tasbih_reset'],True);tap('action_close')
    tap_control(label('zikr_1_name'),'text');shot('counter-phrases',['tasbih_phrase'],True);back()
    swipe(times=2);shot('counter-goals',['tasbih_goal'])
    # Search states and keyboard use actual input components.
    for route in ['city','apps','shop']:
        start(route);type_query('zzzzzzzz')
        shot(route+'-keyboard',[route+'_empty','accessibility'])
        back();shot(route+'-empty',[route+'_empty'],True)
    start('progress');tap_control('personal_progress_clear');shot('progress-confirm',['progress_clear'],True);back()
    start('knowledge');tap_control('learning_clear');shot('learning-confirm',['learning_clear'],True);back()
    # Existing end-to-end resumability and actual overdue-question assertions.
    subprocess.run([sys.executable,'tools/ci/features_controls.py','learning'],check=True)
    # Catalog -> company -> review form -> back; do not submit.
    start('shop_seller');proc=record('company-route');shot('company-detail',['seller'])
    tap('reviews_write');shot('review-sheet',['review_form'],True)
    tap_control('android.widget.EditText','class');shell('input','text','Preview');shot('review-keyboard',['review_form','accessibility']);back();back()
    shot('company-return',['navigation']);back();shot('catalog-return',['shop','navigation']);finish_record(proc,'company-route')

elif GROUP=='accessibility':
    # Synthetic upgrade fixture, signed with the same disposable CI key; no real user data.
    shell('pm','clear',PKG)
    adb('install','-r','-d','fixtures/previous.apk');start('home')
    subprocess.run([sys.executable,'tools/ci/features_controls.py','seed-upgrade'],check=True)
    adb('install','-r','fixtures/current.apk');start('home')
    subprocess.run([sys.executable,'tools/ci/features_controls.py','verify-upgrade'],check=True)
    shot('upgraded-home',['upgrade'])
    theme('system');shell('cmd','uimode','night','no')
    shell('wm','size','720x1280');shell('wm','density','360');shell('settings','put','system','font_scale','2')
    for route in ['home','knowledge','shop','focus','more','city','prayer_settings','alarm','tasbih','qibla','azkar','quiz_cards','places','ramadan','zakat','progress','about','onboarding_welcome','onboarding_perms']:
        start(route);shot(route+'-large',['accessibility'])
        if route in ['prayer_settings','alarm','tasbih','focus','ramadan','more','onboarding_perms']:
            swipe(times=8);shot(route+'-large-bottom',['accessibility'])
    shell('settings','put','system','font_scale','1.3')
    for route,lang in [('language','az'),('prayer_settings','tr'),('shop_seller','az'),('onboarding_city','tr')]:
        start(route,lang);shot(route+'-long-'+lang,['accessibility'])
    shell('settings','put','system','font_scale','1');shell('wm','size','reset');shell('wm','density','reset')
    shell('settings','put','global','animator_duration_scale','0')
    start('home');shot('reduced-motion-home',['motion']);swipe(times=2);shot('reduced-motion-scrolled',['motion','home'])
    start('quiz_cards');shot('reduced-motion-study',['motion','study'])
    shell('settings','put','global','animator_duration_scale','1')

elif GROUP=='focus':
    start('home')
    theme('system')
    subprocess.run([sys.executable,'tools/ci/features_controls.py','focus'],check=True)
    subprocess.run([sys.executable,'tools/ci/features_controls.py','focus-visual'],check=True)
    subprocess.run([sys.executable,'tools/ci/features_controls.py','focus-notifications-denied'],check=True)

(OUT/'complete-'+GROUP+'.txt').write_text('Real Android UI script completed. Images still require visual review.\n')
