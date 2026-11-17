"""Match the published Russian spelling to raw Tanzil token numbers, offline.

The compact keys below are comparison keys only. Neither source text is rewritten.
Only exact matching keys can authorize a highlight. A measured time and an actual
source character span are both required in the reader.
"""
import re
import unicodedata
from difflib import SequenceMatcher


AR_CONSONANTS = dict(zip('بتثجحخدذرزسشصضطظعغفقكلمنهوىي',
                         'бтсжххдзрзсшсдтзъгфкклмнхуйй'))
AR_CONSONANTS.update({'ة': 'т', 'ء': 'ъ', 'أ': 'ъ', 'إ': 'ъ', 'ؤ': 'ъ',
                      'ئ': 'ъ', 'ٔ': 'ъ', 'آ': 'ъа', 'ٱ': '', 'ا': 'а'})
VOWELS = {'َ': 'а', 'ُ': 'у', 'ِ': 'и', 'ً': 'ан', 'ٌ': 'ун', 'ٍ': 'ин',
          'ٰ': 'а', 'ۥ': 'у', 'ۦ': 'и', 'ۧ': 'и'}
SUN = set('تثدذرزسشصضطظلن')
OPENINGS = {'الم', 'المص', 'الر', 'المر', 'كهيعص', 'طه', 'طسم', 'طس', 'يس', 'ص', 'حم', 'عسق', 'ق', 'ن'}
LETTER_NAMES = dict(zip('المصركهيعطسحقن',
                        'алиф лам мим сад ра каф ха йа ъаин та син ха каф нун'.split()))
# The edition consistently gives a consonant carried by the article to the
# previous printed syllable, e.g. «раббил 'аламиин». Snap to that printed
# boundary when there is just one such consonant, not for glued lexical roots.
WITHOUT_VOWELS = str.maketrans('', '', 'аиу')
PRINTED_WORD = re.compile(r"\S+")


def _arabic_key(token, first, last):
    letters = ''.join(c for c in token if c in AR_CONSONANTS)
    if letters in OPENINGS and not any(c in token for c in 'ًٌٍَُِ'):
        return ''.join(LETTER_NAMES[c] for c in letters)
    groups = []
    for c in token:
        if c in AR_CONSONANTS and c != 'ٔ':
            groups.append([c, ''])
        elif groups:
            groups[-1][1] += c
    pieces = []
    for i, (letter, marks) in enumerate(groups):
        glyph = AR_CONSONANTS[letter]
        terminal = last and i == len(groups) - 1
        if '۟' in marks or (letter in 'اى' and i and 'ً' in groups[i-1][1]) or (
                letter == 'ل' and i and groups[i-1][0] in 'ٱل' and 'ّ' not in marks
                and i+1 < len(groups) and groups[i+1][0] in SUN):
            continue
        if letter == 'ٱ' and first and i == 0:
            glyph = 'а' if letters.startswith('ٱل') else ('у' if 'ُ' in token[:5] else 'и')
        if letter == 'ى' and not marks.strip('ْٰٓ'):
            if 'ْ' in marks:
                glyph = 'й'
            else:
                glyph = 'а' if (i and 'َ' in groups[i-1][1]) or 'ٰ' in marks else 'и'
        if letter == 'ي' and not marks.strip('ٓۦ') and i and 'ِ' in groups[i-1][1]:
            glyph = 'и'
        if letter == 'و' and 'ٰ' in marks and 'َ' not in marks:
            glyph = 'а'
        if terminal and letter == 'ة':
            glyph = 'х'
        sound = glyph + ''.join(VOWELS.get(m, '') for m in marks if not terminal or m == 'ٰ')
        pieces.append(sound)
    return ''.join(pieces)


def _compact(raw):
    """Vowel length, gemination and the glottal stop vary in Russian spelling."""
    key = []
    spans = []
    for i, c in enumerate(raw.lower().replace('a', 'а')):
        if c == 'ъ' or c in "'`’‘":
            continue
        if unicodedata.combining(c):
            if spans:
                spans[-1][1] = i + 1
            continue
        if not c.isalpha():
            continue
        if key and key[-1] == c and not raw[spans[-1][1]:i].strip(" 'ъ`’‘"):
            spans[-1][1] = i + 1
        else:
            key.append(c)
            spans.append([i, i+1])
    return ''.join(key), spans


def _anchors(a, b):
    matcher = SequenceMatcher(None, a, b, autojunk=False)
    return {i+k: j+k for i, j, size in matcher.get_matching_blocks() for k in range(size)}


def _spelling_keys(token, first, last, following):
    # At a pause the case ending can drop even in the middle of an ayah. The
    # source edition writes both stopped and continuous forms across verses.
    keys = {_compact(_arabic_key(token, first, stop))[0] for stop in (last, True)}
    if token.startswith('ٱل'):
        keys |= {k[1:] for k in keys if k.startswith('л') and len(k) > 1}
    if 'ً' in token and token.rstrip('۟').endswith(('ا', 'ى')):
        keys |= {k[:-1] for k in keys if k.endswith('ан')}
    if 'ة' in token:
        keys |= {k[:-1] for k in keys if k.endswith('ах')}
    if 'ن' in token or any(mark in token for mark in 'ًٌٍ'):
        next_letter = next((AR_CONSONANTS[c] for c in following if c in AR_CONSONANTS), '')
        assimilated = {'б': 'м', 'й': 'й', 'л': 'л', 'м': 'м', 'р': 'р', 'у': 'у'}.get(next_letter)
        if assimilated:
            keys |= {_compact(k[:-1] + assimilated)[0] for k in keys if k.endswith('н')}
    if following.startswith('ٱل'):
        next_letters = [c for c in following if c in AR_CONSONANTS]
        link = (AR_CONSONANTS[next_letters[2]] if len(next_letters) > 2
                and next_letters[2] in SUN and not following.startswith('ٱلّ') else 'л')
        keys |= {_compact(k + link)[0] for k in keys}
    if 'يِ' in token or 'ىِ' in token:
        keys |= {k.replace('йи', 'и') for k in keys}
    return keys - {''}


def _fill_gaps(parts, actual, ranges):
    # Accept an entire printed word outside the first pass only when the gap is
    # bounded by lexical matches on either side (or a verse edge), its original
    # word count agrees and the printed word's own phonetic key matches.
    bounds = [(-1, 0, 0)]
    bounds.extend((i, *ranges[pos]) for i, (pos, _) in enumerate(parts) if pos in ranges)
    bounds.append((len(parts), len(actual), len(actual)))
    for (previous, _, lo), (following_ix, hi, _) in zip(bounds, bounds[1:]):
        if following_ix - previous <= 1:
            continue
        printed = list(PRINTED_WORD.finditer(actual, lo, hi))
        if following_ix - previous == 2 and len(printed) >= 2:
            i = previous+1
            pos, token = parts[i]
            following = parts[i+1][1] if i+1 < len(parts) else ''
            start, end = printed[0].start(), printed[-1].end()
            keys = _spelling_keys(token, i == 0, i == len(parts)-1, following)
            if _compact(actual[start:end])[0] in keys:
                ranges[pos] = (start, end)
            continue
        if len(printed) != following_ix - previous - 1:
            continue
        for i, match in enumerate(printed, previous+1):
            pos, token = parts[i]
            following = parts[i+1][1] if i+1 < len(parts) else ''
            keys = _spelling_keys(token, i == 0, i == len(parts)-1, following)
            published = _compact(match.group())[0]
            if published in keys:
                ranges[pos] = match.span()
            elif len(printed) == 1 and (not lo or actual[lo-1].isspace() or actual[lo].isspace()) and (
                    hi == len(actual) or actual[hi].isspace() or actual[hi-1].isspace()):
                # Exactly one whole printed Russian word between two exclusive
                # anchors. Match its consonants for variable vowels at stops,
                # hamza and madd, requiring the full, nonempty skeleton.
                core = published.translate(WITHOUT_VOWELS)
                if len(core) >= 2 and any(k.translate(WITHOUT_VOWELS) == core for k in keys):
                    ranges[pos] = match.span()


def word_ranges(arabic, cyrillic):
    parts = [(i, token) for i, token in enumerate(arabic.split(), 1)
             if any(c in AR_CONSONANTS for c in token)]
    if not parts:
        return {}
    expected = []
    offsets = []
    for ix, (position, token) in enumerate(parts):
        key, _ = _compact(_arabic_key(token, ix == 0, ix == len(parts)-1))
        offsets.append((position, len(expected), len(expected) + len(key)))
        expected.extend(key)
    native = ''.join(expected)
    actual, spans = _compact(cyrillic)
    matched = _anchors(native, actual)
    ranges = {}
    for pos, left, right in offsets:
        if right <= left:
            continue
        p, q = matched.get(left), matched.get(right-1)
        if p is None or q is None or actual[p:q+1] != native[left:right]:
            continue
        start, end = spans[p][0], spans[q][1]
        # A shared sound can be spelt on both sides of a space, e.g. the
        # first 'а' of «уама анзала». Keep the copy on the correct side.
        if p != q:
            boundary = cyrillic.rfind(' ', start, spans[p][1])
            if boundary >= 0:
                start = boundary + 1
            boundary = cyrillic.find(' ', spans[q][0], end)
            if boundary >= 0:
                end = boundary
        # One article consonant can be printed at the end of the previous
        # Russian word. Keep highlights on the visible words in this edition.
        if start and not cyrillic[start-1].isspace():
            gap = cyrillic.find(' ', start, end)
            if gap >= 0 and len(_compact(cyrillic[start:gap])[0]) <= 1:
                start = gap + 1
        if end < len(cyrillic) and not cyrillic[end].isspace():
            gap = cyrillic.rfind(' ', start, end)
            if gap >= 0 and len(_compact(cyrillic[gap+1:end])[0]) <= 1:
                end = gap
        if end < len(cyrillic) and cyrillic[end] in "рлнтсдзш'":
            tail = end + 1
            while tail < len(cyrillic) and unicodedata.combining(cyrillic[tail]):
                tail += 1
            if tail == len(cyrillic) or cyrillic[tail].isspace():
                end = tail
        # Recover the author's leading mark for ayn from the original offsets.
        while start > 0 and cyrillic[start-1] in "'`’‘":
            start -= 1
        if start < end:
            ranges[pos] = (start, end)
    # Glued Qamar words can share a doubled letter with their neighbour. A
    # span must have an exclusive owner before it can drive seeking.
    ordered = sorted(ranges)
    conflicts = set()
    for l, r in zip(ordered, ordered[1:]):
        if ranges[l][1] > ranges[r][0]:
            conflicts.update((l, r))
    for k in conflicts:
        del ranges[k]
    _fill_gaps(parts, cyrillic, ranges)
    # Keep a glued word only when its Arabic tokens account for the complete
    # printed word. Merge touching ranges into components and discard a
    # partial component even when several of its internal boundaries matched.
    groups = []
    for i, (pos, _) in enumerate(parts):
        if pos not in ranges:
            continue
        lo, hi = ranges[pos]
        if ' ' in cyrillic[lo:hi] and (lo and not cyrillic[lo-1].isspace()
                                      or hi < len(cyrillic) and not cyrillic[hi].isspace()):
            continue
        if groups and groups[-1][-1][0] == i-1 and groups[-1][-1][3] == lo:
            groups[-1].append((i, pos, lo, hi))
        else:
            groups.append([(i, pos, lo, hi)])
    verified = {}
    for group in groups:
        lo, hi = group[0][2], group[-1][3]
        if (lo == 0 or cyrillic[lo-1].isspace()) and (hi == len(cyrillic) or cyrillic[hi].isspace()):
            verified.update((pos, (start, end)) for _, pos, start, end in group)
    return verified
