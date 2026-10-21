"""The one place the core is chosen. The pilot returns a Zenith sandbox client from `get_core`
instead of the mock, and nothing else in the Gateway changes."""

from ominira.core.base import ZenithCore
from ominira.core.mock import Controls, MockZenith
from ominira.database import SessionFactory
from ominira.settings import settings

_core: ZenithCore | None = None


def get_core() -> ZenithCore:
    global _core
    if _core is None:
        _core = MockZenith(SessionFactory, Controls.for_profile(settings.zenith_profile))
    return _core


def mock_core() -> MockZenith | None:
    core = get_core()
    return core if isinstance(core, MockZenith) else None
