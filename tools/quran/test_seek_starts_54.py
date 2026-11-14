"""Offline regressions for full-verse spelling alignment and measured word ownership."""
import unittest

from import_seek_starts_54 import disjoint_intervals, extract_starts, frame, seek_positions


class SeekAlignmentTest(unittest.TestCase):
    def test_undotted_yeh_only_in_fully_aligned_verse(self):
        tokens = [[1, "فِي"], [2, "شَيۡءٍ"], [3, "بَعِيدٍ"]]
        self.assertEqual(seek_positions("فِى شَىْءٍ بَعِيدٍ", tokens), {1: 1, 2: 2, 3: 3})
        self.assertNotIn(1, seek_positions("فِى شَىْءٍ آخر", tokens))
        self.assertNotIn(1, seek_positions("فِي", [[1, "فِى"]]))

    def test_prelude_keeps_original_token_offsets(self):
        prelude = ("بسم", "الله", "الرحمن", "الرحيم")
        full = "بِسْمِ ٱللَّهِ ٱلرَّحْمَٰنِ ٱلرَّحِيمِ فِى عِلْمٍ"
        self.assertEqual(seek_positions(full, [[1, "فِي"], [2, "عِلْمٍ"]], prelude), {1: 5, 2: 6})

    def test_overlapping_ends_keep_unique_starts_and_only_safe_display(self):
        data = {"audio": {"duration_ms": 600}, "estimated": [1], "words": [
            [1, 1, 0, 150, 1], [1, 2, 100, 260, 1], [1, 3, 190, 430, 1],
            [1, 4, 400, 520, 1], [1, 5, 510, 620, 1],
        ]}
        mapped = {1: {i: i for i in range(1, 6)}}
        windows = {1: (0, 620)}
        existing = frame(b"ZQW1", 1, ">HHII", [(1, 1, 0, 150)])
        new = extract_starts(data, mapped, windows, existing)
        self.assertEqual(new, [(1, 3, 190), (1, 4, 400), (1, 5, 510)])
        self.assertEqual(disjoint_intervals(data, mapped, windows, new),
                         [(1, 3, 260, 400), (1, 4, 430, 510), (1, 5, 520, 600)])

    def test_unknown_first_occurrence_and_duplicate_onsets_are_rejected(self):
        data = {"audio": {"duration_ms": 500}, "words": [
            [1, 1, 0, 90, 2], [1, 2, 100, 190, 1], [1, 3, 100, 290, 1],
        ]}
        self.assertFalse(extract_starts(data, {1: {1: 1, 2: 2, 3: 3}},
                                        {1: (0, 500)}, frame(b"ZQW1", 1, ">HHII", [])))


if __name__ == "__main__":
    unittest.main()
