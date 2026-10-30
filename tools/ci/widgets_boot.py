"""Fresh render and visible launcher widget after a real reboot, without opening the app."""
exec(compile(open('tools/ci/widgets_launcher.py').read().split("shell('pm','clear',PKG)")[0], 'launcher_helpers', 'exec'))
shell('pm','clear',PKG)
shell('am','start','-S','-W','-n',PKG+'/.MainActivity','--es','qa_screen','home','--es','qa_lang','ru','--ez','qa_onboarded','true')
time.sleep(3)
put_pref('online_times','boolean','false');put_pref('appearance','string','light')
probe('refresh');pin('TodayWidget')
probe('inspect');state=json.loads(adb('exec-out','run-as',PKG,'cat','files/widget-probe.json'))
widget_id=next(w['id'] for w in state['widgets'] if w['provider']=='TodayWidget')
def snapshot():
    return json.loads(adb('exec-out','run-as',PKG,'cat',f'files/widget-render-{widget_id}.json'))
before=snapshot();(OUT/'before-reboot.json').write_text(json.dumps(before,indent=2))
shell('input','keyevent','KEYCODE_HOME');adb('reboot');adb('wait-for-device')
deadline=time.monotonic()+180
while shell('getprop','sys.boot_completed').strip()!='1':
    if time.monotonic()>deadline:raise AssertionError('Emulator did not reboot')
    time.sleep(2)
shell('input','keyevent','KEYCODE_WAKEUP');shell('wm','dismiss-keyguard')
# BOOT_COMPLETED and launcher binding finish after sys.boot_completed on this image.
deadline=time.monotonic()+100
after=snapshot()
while after['now']==before['now'] and time.monotonic()<deadline:
    time.sleep(3);after=snapshot()
(OUT/'after-reboot.json').write_text(json.dumps(after,indent=2))
check('new render produced after reboot without app launch',after['now']!=before['now'])
root=show_provider('TodayWidget');capture('visible-widget-after-reboot')
check('schedule remains visible after reboot',find_control(root,'w_time') is not None)
(OUT/'events-completed.txt').write_text('Reboot: fresh render timestamp and actual visible launcher content verified.\n')
