"""Check newly added UI strings in every supported language."""
from pathlib import Path
import re
import xml.etree.ElementTree as ET

res = Path('app/src/main/res')
for name in ('knowledge_level_strings.xml', 'tasbih_craft.xml'):
    def read(folder):
        nodes = list(ET.parse(res / folder / name).getroot())
        strings = {x.get('name'): ''.join(x.itertext()) for x in nodes}
        assert len(strings) == len(nodes), (folder, name, 'duplicate resource')
        assert all(strings.values()), (folder, name, 'empty resource')
        return strings
    base = read('values')
    for lang in ('ru', 'uz', 'tg', 'kk', 'ky', 'az', 'tr'):
        actual = read('values-' + lang)
        assert actual.keys() == base.keys(), (name, lang)
        for key in base:
            assert sorted(re.findall(r'%\d+\$[ds]', base[key])) == sorted(re.findall(r'%\d+\$[ds]', actual[key])), (name, lang, key)
print('Knowledge-level and tasbih resources match in all eight languages')
