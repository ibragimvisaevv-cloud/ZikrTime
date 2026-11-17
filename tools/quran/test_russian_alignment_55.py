"""Regression cases for published Cyrillic spelling / Arabic audio token boundaries."""
import gzip
import json
import unittest

from import_russian_transcription_55 import ASSETS, aligned_row
from russian_alignment_55 import word_ranges


class RussianAlignmentTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.arabic = {}
        for line in (ASSETS / 'quran/sources/quran-uthmani.txt').read_text().splitlines():
            if line and line[0].isdigit():
                surah, ayah, text = line.split('|', 2)
                cls.arabic[int(surah), int(ayah)] = text
        cls.chapters = {}
        for path in (ASSETS / 'quran_transcription/ru').glob('*.qtr'):
            with gzip.open(path, 'rt', encoding='utf-8') as data:
                cls.chapters.update(json.load(data))

    def words(self, surah, ayah):
        text, triples = self.chapters[str(surah)][ayah-1]
        generated = aligned_row(self.arabic[surah, ayah], text, (surah, ayah))
        self.assertEqual(generated, [text, triples])
        return {position: text[start:end] for position, start, end in zip(triples[::3], triples[1::3], triples[2::3])}

    def test_glued_basmala_and_opening_letters(self):
        self.assertEqual(list(self.words(1, 1).values()), ['бисми', 'ллахир', 'рахманир', 'рахиим'])
        self.assertEqual(self.words(2, 1)[5], 'алиф лам мим')
        self.assertEqual(self.words(42, 2)[1], "'аин син к̣аф")

    def test_lookalike_sounds_do_not_borrow_next_word(self):
        ninth = self.words(9, 1)
        self.assertEqual(ninth[4], 'уарасуулихи')
        self.assertEqual(ninth[7], "'ахадттум")
        self.assertEqual(self.words(13, 1)[6], 'тилка')
        for surah, ayah, position, previous_error in (
                (18, 58, 11, "'аз̃аб ба"), (27, 88, 14, 'шай инн'), (74, 31, 52, 'хуу уа')):
            self.assertNotEqual(self.words(surah, ayah).get(position), previous_error)

    def test_substring_is_not_a_corresponding_word(self):
        self.assertEqual(word_ranges('مِنْ', 'минерал'), {})
        self.assertEqual(word_ranges('بِسْمِ', ''), {})

    def test_all_published_spans_have_exclusive_boundaries(self):
        for surah, ayah in self.arabic:
            text, triples = self.chapters[str(surah)][ayah-1]
            spans = {position: (start, end) for position, start, end in zip(triples[::3], triples[1::3], triples[2::3])}
            self.assertTrue(spans, (surah, ayah))
            end_previous = 0
            for position, (start, end) in spans.items():
                with self.subTest(surah=surah, ayah=ayah, position=position):
                    self.assertTrue(end_previous <= start < end <= len(text))
                    if start and not text[start-1].isspace():
                        self.assertEqual(spans[position-1][1], start)
                    if end < len(text) and not text[end].isspace():
                        self.assertEqual(spans[position+1][0], end)
                    if ' ' in text[start:end]:
                        self.assertTrue(start == 0 or text[start-1].isspace())
                        self.assertTrue(end == len(text) or text[end].isspace())
                end_previous = end


if __name__ == '__main__':
    unittest.main()
