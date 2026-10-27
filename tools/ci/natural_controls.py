"""Device-side scenarios: real taps, persisted counts and comparable warm UI measurements."""
import subprocess as s, time, re, xml.etree.ElementTree as E, json, pathlib, sys
OUT=pathlib.Path('ci-out'); PKG='com.zikrtime.app'
def adb(*args): return s.check_output(['adb',*args],text=True)
def shell(*args): return adb('shell',*map(str,args))
def xml():
 shell('uiautomator','dump','/sdcard/current.xml'); return E.fromstring(shell('cat','/sdcard/current.xml'))
def bounds(n): return list(map(int,re.findall(r'\d+',n.attrib['bounds'])))
def tap(n):
 a,b,c,d=bounds(n);shell('input','tap',(a+c)//2,(b+d)//2)
def named(text): return next(n for n in xml().iter('node') if n.attrib.get('text')==text)
def ring():
 nodes=[n for n in xml().iter('node') if n.attrib.get('clickable')=='true']
 return max(nodes,key=lambda n:(bounds(n)[2]-bounds(n)[0])*(bounds(n)[3]-bounds(n)[1]))
def prefs():
 time.sleep(.5);return {n.attrib['name']:n.attrib.get('value',n.text) for n in E.fromstring(adb('exec-out','run-as',PKG,'cat','shared_prefs/zikrtime.xml'))}
def count():return int(prefs()['tasbih_count'])
def launch():
 shell('am','start','-S','-W','-n',PKG+'/.MainActivity','--es','qa_screen','tasbih','--es','qa_lang','ru','--ez','qa_demo','true');time.sleep(3)
def scroll(up):
 a,b,c,d=bounds(next(xml().iter('node')));shell('input','swipe',c//2,int(d*(.8 if up else .3)),c//2,int(d*(.3 if up else .8)),350);time.sleep(.5)
if len(sys.argv)>1:
 # Warmed baseline/current on the same device, no concurrent video encoding.
 for label,apk in [('baseline',sys.argv[1]),('natural',sys.argv[2])]:
  shell('am','force-stop',PKG);adb('uninstall',PKG);adb('install','-r','-g',apk);launch();n=ring();a,b,c,d=bounds(n);x,y=(a+c)//2,(b+d)//2
  shell(f'for n in $(seq 1 10); do input tap {x} {y}; done');time.sleep(1)
  shell('dumpsys','gfxinfo',PKG,'reset')
  before=count();shell(f'for n in $(seq 1 40); do input tap {x} {y}; done');time.sleep(1);after=count();assert after-before==40,(label,before,after)
  (OUT/f'perf-{label}.txt').write_text(shell('dumpsys','gfxinfo',PKG))
  (OUT/f'memory-{label}.txt').write_text(shell('dumpsys','meminfo',PKG))
  shell('dumpsys','gfxinfo',PKG,'reset');time.sleep(5)
  (OUT/f'idle-{label}.txt').write_text(shell('dumpsys','gfxinfo',PKG))
else:
 before=count();scroll(True)
 for goal in ['99','100','∞','33']:
  tap(named(goal));assert count()==before,goal
 scroll(False);tap(named('Сбросить'));assert count()==0
 n=ring();tap(n);tap(n);assert count()==2
 tap(named('Альхамдулиллях'));assert count()==0
 n=ring();tap(n);tap(n);assert count()==2
 # Restart without QA reseeding so persistence is independently checked.
 shell('am','start','-S','-W','-n',PKG+'/.MainActivity','--es','qa_screen','tasbih','--es','qa_lang','ru');time.sleep(2)
 assert count()==2
 (OUT/'controls-passed.json').write_text(json.dumps({'goals':[33,99,100,0],'reset':True,'phrase':True,'restart_count':2}))
