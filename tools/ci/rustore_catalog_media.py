"""Capture only the requested shop and mosque screens from the checked APK."""
exec(compile(open('tools/ci/rustore_media.py').read().split("\nstart('home')")[0], 'media-helpers', 'exec'))


def wait_catalog(key, field):
    deadline = time.monotonic() + 55
    while time.monotonic() < deadline:
        raw = prefs().get(key, '')
        if raw:
            data = json.loads(raw)
            if field in data:
                print('Loaded public catalog:', key, len(data[field]), flush=True)
                return data
        time.sleep(2)
    raise AssertionError('Public catalog did not load: ' + key)


def scroll_content(fraction=.52):
    candidates = []
    for n in ui().iter('node'):
        if n.get('scrollable') != 'true':
            continue
        l,t,r,b = map(int,re.findall(r'-?\d+', n.get('bounds')))
        if b-t > 180 and b-t > (r-l)*.35:
            candidates.append((b-t,l,t,r,b))
    assert candidates, 'No vertical content found'
    _,l,t,r,b=max(candidates)
    shell('input','swipe',l+8,int(t+(b-t)*.85),l+8,int(t+(b-t)*(.85-fraction)),450)
    time.sleep(2)


shell('svc', 'wifi', 'enable')
shell('svc', 'data', 'enable')
start('home')
put_pref('appearance', 'string', 'dark')
start('shop')
shop = wait_catalog('shop_catalog', 'sellers')
time.sleep(5)
capture('06-shop-top')
scroll_content(.46)
capture('06-shop-catalog')
scroll_content(.42)
capture('06-shop-products')

start('places')
places = wait_catalog('places_json', 'places')
tap_control('Мечети', 'text')
time.sleep(12)
capture('07-mosques-map')
# Also capture the native list, allowing selection if map tiles are unavailable.
if find_control(ui(), 'Скрыть карту', 'text') is not None:
    tap_control('Скрыть карту', 'text')
capture('07-mosques-list')

(target / 'source.json').write_text(json.dumps({
    'apk_source': '22bed67f6863addaa1eed71f4bd563f1b6d00ad4',
    'apk_sha256': 'd1048c731153662808682401cfe48dc9e4638f5216bf0656ff7d4efacaefdb44',
    'method': 'Unaltered Android 15 screenshots; normal online app catalog requests and UI navigation; no compositing or invented listings.',
    'size': [1080,1920], 'language':'ru', 'theme':'dark',
    'shop_demo':shop.get('demo'), 'sellers':len(shop['sellers']),
    'places':len(places['places']), 'screenshots':5,
}, indent=2))
