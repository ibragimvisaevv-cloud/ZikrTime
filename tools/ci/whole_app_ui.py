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
    # Wait for the real composed state after an interaction, not only the updated preference.
    time.sleep(1.5)
    settled = ui()
    screenshot(name)
    if hierarchy: (OUT/(name+'.xml')).write_bytes(ET.tostring(settled))
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
    put_pref('alarm_on','boolean','true')
    put_pref('blocking','boolean','true')
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
            if route in ['shop','places']: time.sleep(14)
            if route == 'quiz_cards' and find_control(ui(), 'learning_read') is not None: tap_control('learning_read')
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
    start('tasbih');tap_control(label('action_reset'),'content-desc');shot('counter-confirm',['tasbih_reset'],True);tap('action_close')
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
            swipe(times=18);shot(route+'-large-bottom',['accessibility'])
    shell('settings','put','system','font_scale','1.3')
    for route,lang in [('language','az'),('prayer_settings','tr'),('shop_seller','az'),('onboarding_city','tr')]:
        start(route,lang);shot(route+'-long-'+lang,['accessibility'])
    shell('settings','put','system','font_scale','1');shell('wm','size','reset');shell('wm','density','reset')
    shell('settings','put','global','animator_duration_scale','0')
    start('home');shot('reduced-motion-home',['motion']);swipe(times=2);shot('reduced-motion-scrolled',['motion','home'])
    start('quiz_cards');shot('reduced-motion-study',['motion','study'])
    shell('settings','put','global','animator_duration_scale','1')

elif GROUP=='details':
    # Independent subroutes keep collecting evidence if one external service is unavailable.
    import traceback
    failures=[]
    def case(name, fn):
        try: fn()
        except Exception as error:
            failures.append({'case':name,'error':str(error)})
            (OUT/(name+'-failure.txt')).write_text(traceback.format_exc())
            try: shot(name+'-failure')
            except Exception: pass
            print('CASE FAILED',name,error,flush=True)
    start('home');theme('light')
    bank={q['id']:q for q in json.loads(Path('app/src/main/assets/quiz/ru.json').read_text())['questions']}
    def answer(scope, correct=True):
        session=learning()['sessions'][scope];q=bank[session['ids'][session['index']]]
        canonical=q['answer'] if correct else (q['answer']+1)%len(q['options'])
        tap_control(q['options'][canonical],'text')
        return q
    def quiz():
        put_pref('learning_state_v1','string','{}');start('quiz')
        proc=record('quiz-route')
        try:
            tap_control('learning_skip');shot('quiz-question',['quiz_question'])
            answer('normal',False);swipe();shot('quiz-wrong',['quiz_feedback','source'])
            tap_control('learning_next')
            for i in range(3):
                answer('normal');swipe();shot('quiz-correct-'+str(i),['quiz_feedback'])
                tap_control('learning_finish' if i==2 else 'learning_next')
            assert learning()['sessions']['normal']['stage']=='RESULT'
            shot('quiz-result',['quiz_result']);tap('quiz_again');shot('quiz-again',['quiz_result'])
            back();shot('quiz-return',['navigation'])
        finally: finish_record(proc,'quiz-route')
    case('quiz',quiz)
    def azkar():
        put_pref('azkar_reading_v1','string','{}');start('azkar')
        for i in range(5): tap('azkar_next')
        shot('azkar-last',['azkar_navigation']);tap('azkar_complete')
        assert json.loads(prefs()['azkar_reading_v1'])['morning']['complete'] is True
        shot('azkar-finished',['azkar_navigation'])
    case('azkar',azkar)
    def counter():
        put_pref('tasbih_count','int','32');put_pref('tasbih_target','int','33');start('tasbih')
        tap_control(label('tasbih_title'),'content-desc')
        assert prefs()['tasbih_count']=='33'
        shot('counter-complete',['tasbih','tasbih_goal'])
        tap_control(label('action_reset'),'content-desc');tap('action_reset')
        assert prefs()['tasbih_count']=='0'
        shot('counter-reset',['tasbih_reset'])
    case('counter',counter)
    def alarm():
        put_pref('alarm_on','boolean','false');start('alarm');shot('alarm-disabled',['alarm'])
        put_pref('alarm_wake_check','boolean','true');start('alarm_ring');tap('alarm_answer_to_stop')
        shot('alarm-question',['alarm_quiz']);answer('alarm');swipe();shot('alarm-answer',['alarm_quiz'])
        tap_control('learning_finish');shot('alarm-stopped',['alarm_quiz','navigation'])
    case('alarm',alarm)
    def blocked():
        start('block_limit')
        tap_control(label('block_quiz_button').replace('%1$d','3').replace('%2$d','10'),'text')
        shot('block-question',['block_quiz'])
        for i in range(3):
            swipe(up=False,times=5);answer('blocking');tap_control('learning_finish' if i==2 else 'learning_next')
        shot('block-result',['block_quiz'])
        tap('action_done')
    case('block',blocked)
    def denied():
        shell('pm','revoke',PKG,'android.permission.ACCESS_COARSE_LOCATION')
        shell('pm','clear-permission-flags',PKG,'android.permission.ACCESS_COARSE_LOCATION','user-set','user-fixed')
        start('onboarding_city',onboarded=False);tap('city_locate')
        shot('location-native-permission',['onboarding_location'])
        tap_control('permission_deny_button');shot('onboarding-location-denied',['onboarding_location'])
        start('city');tap('city_locate')
        root=ui()
        if find_control(root,'permission_deny_button') is not None: tap_control('permission_deny_button')
        shot('city-location-denied',['city_denied'])
    case('location',denied)
    def places():
        start('places');time.sleep(14);tap('place_hide_map')
        tap_control('place_card');shot('place-expanded',['place_detail'])
        tap('place_changed');shot('place-correction',['place_correction']);tap('place_close')
        tap('places_rooms');shot('place-filtered',['places']);back();shot('places-back',['navigation'])
    case('places',places)
    def disclosure():
        start('focus');tap_control('focus_accessibility_disclosure')
        shot('focus-disclosure',['focus_disclosure']);back()
    case('disclosure',disclosure)
    def zakat():
        put_pref('zakat_inputs','string','{"cash":"","gold_price":"10000","silver_price":"100"}')
        start('zakat');type_query('1000000');shot('zakat-keyboard',['zakat','accessibility']);back()
        assert json.loads(prefs()['zakat_inputs'])['cash']=='1000000'
        swipe(up=False,times=5);shot('zakat-calculated',['zakat'])
        shell('svc','wifi','disable');shell('svc','data','disable')
        try:
            tap('zakat_update_prices');time.sleep(28);swipe(times=8);shot('zakat-offline',['zakat_prices'])
            locate_control(label('zakat_prices_failed'),'text')
        finally:
            shell('svc','wifi','enable');shell('svc','data','enable')
    case('zakat',zakat)
    (OUT/'details-results.json').write_text(json.dumps({'failures':failures},ensure_ascii=False,indent=2))
    if failures: raise AssertionError(str(failures))

elif GROUP=='focus':
    start('home')
    theme('system')
    shell('pm','grant',PKG,'android.permission.POST_NOTIFICATIONS')
    subprocess.run([sys.executable,'tools/ci/features_controls.py','focus'],check=True)
    subprocess.run([sys.executable,'tools/ci/features_controls.py','focus-visual'],check=True)
    subprocess.run([sys.executable,'tools/ci/features_controls.py','focus-notifications-denied'],check=True)

(OUT/('complete-'+GROUP+'.txt')).write_text('Real Android UI script completed. Images still require visual review.\n')
