"""Speech providers. Callers use ominira.speech.provider, which picks one by SPEECH_PROVIDER."""


class SpeechUnavailable(Exception):
    def __init__(self, code: str, detail: str = ""):
        super().__init__(f"{code}: {detail}" if detail else code)
        self.code = code
