from __future__ import annotations

from app.application.ports import LLMProvider
from app.config import Settings
from app.infrastructure.providers.chatgpt import ChatGptOAuthProvider
from app.infrastructure.providers.mock import MockLLMProvider
from app.infrastructure.providers.openai import OpenAILLMProvider


class FallbackLLMProvider:
    def __init__(self, primary: LLMProvider, fallback: LLMProvider) -> None:
        self._primary = primary
        self._fallback = fallback
        self.name = primary.name
        self.model = primary.model

    def interpret(self, request):
        try:
            return self._primary.interpret(request)
        except (RuntimeError, ValueError):
            return self._fallback.interpret(request)


def build_provider(config: Settings) -> LLMProvider:
    provider = config.provider.strip().lower()
    if provider == "mock" or not config.enabled:
        selected: LLMProvider = MockLLMProvider()
    elif provider == "openai":
        selected = OpenAILLMProvider(config)
    elif provider == "openai_chatgpt":
        selected = ChatGptOAuthProvider(config)
    else:
        raise ValueError(f"Unsupported AI Engine provider: {config.provider}")
    if not config.fallback_provider or config.fallback_provider.strip().lower() == provider:
        return selected
    fallback_config = config.model_copy(update={"provider": config.fallback_provider, "fallback_provider": None})
    return FallbackLLMProvider(selected, build_provider(fallback_config))
