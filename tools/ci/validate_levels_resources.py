"""Validate all newly added learning/tasbih/topic strings in eight locales."""
from pathlib import Path
import re
import xml.etree.ElementTree as ET

res = Path('app/src/main/res')
names = ('knowledge_level_strings.xml', 'tasbih_craft.xml', 'training_strings.xml', 'quran_topics.xml')
# Java/Android Formatter placeholders, including indexed, width and literal %%.
format_pattern = re.compile(r'%(?:\d+\$)?[-#+ 0,(<]*\d*(?:\.\d+)?[tT]?[a-zA-Z%]')

for name in names:
    def read(folder):
        root = ET.parse(res / folder / name).getroot()
        assert root.tag == 'resources', (folder, name, 'wrong XML root')
        nodes = list(root)
        assert all(x.tag == 'string' and x.get('name') for x in nodes), (folder, name, 'unexpected resource')
        strings = {x.get('name'): ''.join(x.itertext()) for x in nodes}
        assert len(strings) == len(nodes), (folder, name, 'duplicate resource')
        assert all(text.strip() for text in strings.values()), (folder, name, 'empty resource')
        return strings
    base = read('values')
    assert base, (name, 'empty file')
    for lang in ('ru', 'uz', 'tg', 'kk', 'ky', 'az', 'tr'):
        actual = read('values-' + lang)
        assert actual.keys() == base.keys(), (name, lang, 'key set mismatch')
        for key in base:
            assert sorted(format_pattern.findall(base[key])) == sorted(format_pattern.findall(actual[key])), \
                (name, lang, key, 'format placeholders differ')
print('Learning levels, tasbih, training and Quran topic resources match in all eight languages')
