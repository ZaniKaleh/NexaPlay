import unittest

import nexaplay_engine as engine


class NexaPlayEngineTests(unittest.TestCase):
    def test_audio_extensions(self):
        self.assertEqual(
            set(engine.supported_extensions("audio")),
            {".mp3", ".aac", ".m4a", ".flac", ".wav"},
        )

    def test_video_extensions(self):
        self.assertEqual(set(engine.supported_extensions("video")), {".mp4", ".mkv"})

    def test_extension_is_case_insensitive(self):
        self.assertTrue(engine.supports_file("Concert.MKV", "video"))
        self.assertTrue(engine.supports_file("Song.MP3", "audio"))
        self.assertFalse(engine.supports_file("notes.txt", "audio"))

    def test_repeat_mode_cycles(self):
        self.assertEqual(engine.next_repeat_mode(engine.REPEAT_OFF), engine.REPEAT_ONE)
        self.assertEqual(engine.next_repeat_mode(engine.REPEAT_ONE), engine.REPEAT_PLAYLIST)
        self.assertEqual(engine.next_repeat_mode(engine.REPEAT_PLAYLIST), engine.REPEAT_OFF)

    def test_repeat_labels(self):
        self.assertEqual(engine.repeat_label(0), "Repeat: Off")
        self.assertEqual(engine.repeat_label(1), "Repeat: 1")
        self.assertEqual(engine.repeat_label(2), "Repeat: Playlist")

    def test_next_index_repeat_all_wraps(self):
        self.assertEqual(engine.next_index(2, 3, 1, engine.REPEAT_PLAYLIST), 0)
        self.assertEqual(engine.next_index(0, 3, -1, engine.REPEAT_PLAYLIST), 2)

    def test_next_index_repeat_off_stops_at_ends(self):
        self.assertEqual(engine.next_index(2, 3, 1, engine.REPEAT_OFF), -1)
        self.assertEqual(engine.next_index(0, 3, -1, engine.REPEAT_OFF), -1)

    def test_next_index_repeat_one_keeps_current(self):
        self.assertEqual(engine.next_index(1, 3, 1, engine.REPEAT_ONE), 1)

    def test_empty_playlist(self):
        self.assertEqual(engine.next_index(0, 0, 1, engine.REPEAT_PLAYLIST), -1)


if __name__ == "__main__":
    unittest.main()
