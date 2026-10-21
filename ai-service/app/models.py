"""LLM adapter: mock mode is explicitly marked; never executes a business tool."""
from typing import Protocol

from langchain_core.messages import AIMessage, BaseMessage, SystemMessage

from app.config import Settings

SCAFFOLD_REPLY = (
    "顺手智能助手的阶段1服务已连接。当前尚未接入校园任务、服务橱窗或交易接口，"
    "不能发布任务、预约服务或抢单。"
)
SYSTEM_PROMPT = (
    "你是顺手校园互助平台的阶段1接入测试助手。"
    "你尚无任何业务查询、交易或支付工具。"
    "不得声称已查询真实任务、服务、余额，不得捏造 ID、报价或交易成功。"
    "对真实业务请求，说明尚未接入并提示用户使用原有业务页面。"
)


class Responder(Protocol):
    async def respond(self, history: list[BaseMessage]) -> str: ...


class MockResponder:
    async def respond(self, history: list[BaseMessage]) -> str:
        return SCAFFOLD_REPLY


class OpenAICompatibleResponder:
    def __init__(self, settings: Settings):
        from langchain_openai import ChatOpenAI

        self.client = ChatOpenAI(
            model=settings.model_name,
            api_key=settings.model_api_key,
            base_url=settings.model_base_url,
            request_timeout=settings.request_timeout_seconds,
            max_retries=0,
            temperature=0,
        )
        self.max_history_messages = settings.max_history_messages

    async def respond(self, history: list[BaseMessage]) -> str:
        result: AIMessage = await self.client.ainvoke(
            [SystemMessage(content=SYSTEM_PROMPT), *history[-self.max_history_messages :]]
        )
        if not isinstance(result.content, str):
            return "当前模型返回的内容无法显示，请稍后重试。"
        return result.content


def build_responder(settings: Settings) -> Responder:
    if settings.model_provider == "mock":
        return MockResponder()
    return OpenAICompatibleResponder(settings)
