"""Small, dependency-free Python logic used by the NexaPlay Android app."""

from __future__ import annotations

from pathlib import PurePath
from typing import Final

VIDEO_EXTENSIONS: Final[frozenset[str]] = frozenset({".mp4", ".mkv"})
AUDIO_EXTENSIONS: Final[frozenset[str]] = frozenset(
    {".mp3", ".aac", ".m4a", ".flac", ".wav"}
)
PLAYLIST_EXTENSIONS: Final[frozenset[str]] = frozenset({".m3u", ".m3u8"})

REPEAT_OFF: Final[int] = 0
REPEAT_ONE: Final[int] = 1
REPEAT_PLAYLIST: Final[int] = 2


def normalize_extension(filename: str) -> str:
    """Return a lowercase file extension, including the leading dot."""
    return PurePath(str(filename).strip()).suffix.lower()


def supported_extensions(media_type: str) -> list[str]:
    """Return supported file extensions for the requested media category."""
    category = str(media_type).strip().lower()
    if category in {"audio", "music"}:
        return sorted(AUDIO_EXTENSIONS)
    if category in {"video", "movie"}:
        return sorted(VIDEO_EXTENSIONS)
    if category in {"playlist", "playlists"}:
        return sorted(PLAYLIST_EXTENSIONS)
    raise ValueError(f"Unsupported media category: {media_type!r}")


def supports_file(filename: str, media_type: str) -> bool:
    """Check whether a filename belongs to the selected media category."""
    category = str(media_type).strip().lower()
    extension = normalize_extension(filename)
    return extension in set(supported_extensions(category))


def next_repeat_mode(current_mode: int) -> int:
    """Cycle repeat mode: off -> one -> playlist -> off."""
    try:
        current = int(current_mode)
    except (TypeError, ValueError):
        current = REPEAT_OFF
    if current not in {REPEAT_OFF, REPEAT_ONE, REPEAT_PLAYLIST}:
        current = REPEAT_OFF
    return (current + 1) % 3


def repeat_label(mode: int) -> str:
    """Return the localized label displayed by the Android controls."""
    labels = {
        REPEAT_OFF: "Repeat: Off",
        REPEAT_ONE: "Repeat: 1",
        REPEAT_PLAYLIST: "Repeat: Playlist",
    }
    try:
        return labels.get(int(mode), labels[REPEAT_OFF])
    except (TypeError, ValueError):
        return labels[REPEAT_OFF]


def next_index(current_index: int, item_count: int, direction: int, repeat_mode: int) -> int:
    """Calculate a safe next/previous playlist index for unit tests and queue logic.

    Returns -1 when the queue is empty or when repeat is off and the requested
    movement would leave the queue. Repeat-one intentionally keeps the current
    item selected; repeat-all wraps around.
    """
    if item_count <= 0:
        return -1

    index = max(0, min(int(current_index), item_count - 1))
    step = 1 if int(direction) >= 0 else -1

    if int(repeat_mode) == REPEAT_ONE:
        return index

    candidate = index + step
    if 0 <= candidate < item_count:
        return candidate

    if int(repeat_mode) == REPEAT_PLAYLIST:
        return 0 if step > 0 else item_count - 1

    return -1
