"""Stage 3: structured demand clarification -> same-campus offers -> LangGraph HITL.

No business write Tool is registered. Confirmation, password and real transaction
are entirely delegated to authenticated Java /api/agent/drafts and /operations.
LLM never supplies principal or financially authoritative price values.
"""
import logging
import re
from datetime import datetime
from typing import Annotated, Literal
from zoneinfo import ZoneInfo

from langchain_core.messages import AIMessage, AnyMessage
from langgraph.graph import START, END, StateGraph
from langgraph.graph.message import add_messages
from langgraph.types import interrupt
from pydantic import BaseModel, Field
from typing_extensions import TypedDict

from app.clients.java_backend import JavaReadOnlyClient, JavaToolError
from app.config import Settings
from app.security import Actor

logger = logging.getLogger(__name__)
CAMPUS_TZ = ZoneInfo("Asia/Shanghai")
VALID_TYPES = {"代取快递", "代拿餐食", "校内代办", "代购物品", "通用代办"}
ALLOWED_FIELDS = {
    "category", "sub_type", "description", "pickup_address",
    "delivery_area", "delivery_address_id", "tip", "delivery_fee",
    "product_cost", "shopping_budget", "max_budget", "deadline_text", "pages",
}
MONEY_RE = r"([0-9]+(?:\.[0-9]{1,2})?)"


class DemandPatch(BaseModel):
    category: str | None = None
    sub_type: str | None = None
    description: str | None = Field(default=None, max_length=256)
    pickup_address: str | None = Field(default=None, max_length=255)
    delivery_area: str | None = None
    delivery_address_id: int | None = None
    tip: float | None = None
    delivery_fee: float | None = None
    product_cost: float | None = None
    shopping_budget: float | None = None
    max_budget: float | None = None
    deadline_text: str | None = None
    pages: int | None = Field(default=None, ge=1, le=1000)


class DemandExtractor:
    """Mock supplies deterministic extraction; production uses structured output with safe fallback."""
    def __init__(self, settings: Settings):
        self.model = None
        if settings.model_provider == "openai_compatible":
            from langchain_openai import ChatOpenAI
            self.model = ChatOpenAI(
                model=settings.model_name,
                api_key=settings.model_api_key,
                base_url=settings.model_base_url,
                request_timeout=settings.request_timeout_seconds,
                max_retries=0,
                temperature=0,
            ).with_structured_output(DemandPatch, method="json_schema")

    async def extract(self, message: str, previous: dict) -> tuple[dict, bool]:
        if self.model:
            try:
                result = await self.model.ainvoke(
                    [
                        ("system",
                         "你是校园互助需求字段提取器，只抽取用户明确提供的字段。"
                         "预算上限 max_budget 不是小费 tip、配送费 delivery_fee 或商品预算 shopping_budget。"
                         "不可编造用户地址簿ID、金额、真实地点坐标或订单ID。"
                         "大类只能是代取快递、代拿餐食、校内代办、代购物品、通用代办。"
                         "没有明确字段就输出null。不要把支付密码等秘密提取进任何字段。"),
                        ("human", f"已有非敏感字段：{previous}\n用户新消息：{message}"),
                    ])
                if isinstance(result, DemandPatch):
                    return result.model_dump(exclude_none=True), False
            except Exception:
                logger.warning("demand_extraction_model_unavailable")
        return self._rule_extract(message), self.model is not None

    @staticmethod
    def _chinese_number(raw: str) -> int | None:
        digits = {c: i for i, c in enumerate("零一二三四五六七八九")}
        if raw.isdigit():
            return int(raw)
        if raw in digits:
            return digits[raw]
        if raw == "十":
            return 10
        if "十" in raw:
            tens, _, ones = raw.partition("十")
            a = digits.get(tens, 1) if tens else 1
            b = digits.get(ones, 0) if ones else 0
            return a * 10 + b
        return None

    @staticmethod
    def _rule_extract(message: str) -> dict:
        """Deliberately conservative: no guessed fees, addresses or time guarantees."""
        values: dict = {}
        if "打印" in message:
            values["category"], values["sub_type"] = "校内代办", "资料打印"
        elif any(x in message for x in ("咖啡", "奶茶", "外卖", "取餐")):
            values["category"] = "代拿餐食"
            values["sub_type"] = "奶茶咖啡代取" if any(x in message for x in ("咖啡", "奶茶")) else "校内餐饮"
        elif "快递" in message:
            values["category"] = "代取快递"
        elif "代购" in message or "买东西" in message:
            values["category"] = "代购物品"
        elif "校内代办" in message:
            values["category"] = "校内代办"
        # An explicit pickup address is required. Do not infer from "送到X".
        pickup = re.search(r"(?:取件地点|取货地点|取件地址|从)[：:\s]*([^，,。；;]{2,40}?)(?=送到|到|，|,|。|；|;|$)", message)
        if pickup:
            values["pickup_address"] = pickup.group(1).strip()
        delivery = re.search(r"(?:送到|送至|目的地)[：:\s]*([^，,。；;]{2,40})", message)
        if delivery:
            values["delivery_area"] = delivery.group(1).strip()
        address_id = re.search(r"地址(?:簿)?ID[：:\s]*(\d{1,12})", message, re.I)
        if address_id:
            values["delivery_address_id"] = int(address_id.group(1))
        for field, title in (("tip", "小费"), ("delivery_fee", "配送费"),
                             ("product_cost", "商品费"), ("shopping_budget", "商品预算")):
            match = re.search(title + r"[：:\s]*" + MONEY_RE, message)
            if match:
                values[field] = float(match.group(1))
        cap = re.search(r"(?:总预算|最多|预算)[：:\s]*" + MONEY_RE, message)
        if cap:
            values["max_budget"] = float(cap.group(1))
        pages = re.search(r"([0-9]{1,4}|[零一二三四五六七八九十]{1,4})页", message)
        if pages:
            quantity = DemandExtractor._chinese_number(pages.group(1))
            if quantity is not None and 1 <= quantity <= 1000:
                values["pages"] = quantity
        # A spoken total budget must never become an invented service tip.
        if "max_budget" not in values:
            cap_han = re.search(r"预算[：:\s]*([一二三四五六七八九十]{1,4})[块元]", message)
            if cap_han:
                amount = DemandExtractor._chinese_number(cap_han.group(1))
                if amount is not None:
                    values["max_budget"] = float(amount)
        if "明天" in message or "今晚" in message or "截止" in message or "点前" in message:
            values["deadline_text"] = "用户要求的时间（当前任务表不具有可强制的交付截止字段）"
        if any(x in message for x in ("帮我", "需要", "想要", "请人", "打印", "咖啡", "奶茶")):
            values["description"] = message[:256]
        return values


class DelegationState(TypedDict, total=False):
    messages: Annotated[list[AnyMessage], add_messages]
    slots: dict
    offers: list[dict]
    status: str
    question: str
    model_degraded: bool
    preview_input: dict
    turns: int


def _currency(value) -> float | None:
    try:
        number = float(value)
        if 0 <= number <= 99999999.99 and round(number, 2) == number:
            return number
    except (ValueError, TypeError, OverflowError):
        pass
    return None


def validate_patch(patch: dict) -> dict:
    clean = {k: v for k, v in patch.items() if k in ALLOWED_FIELDS and v is not None}
    if clean.get("category") not in VALID_TYPES:
        clean.pop("category", None)
    for key in ("tip", "delivery_fee", "product_cost", "shopping_budget", "max_budget"):
        if key in clean:
            cost = _currency(clean[key])
            if cost is None:
                clean.pop(key)
            else:
                clean[key] = cost
    if "pages" in clean and (
        type(clean["pages"]) is not int or not 1 <= clean["pages"] <= 1000
    ):
        clean.pop("pages")
    if "delivery_address_id" in clean and (
        type(clean["delivery_address_id"]) is not int or clean["delivery_address_id"] <= 0
    ):
        clean.pop("delivery_address_id")
    if "pickup_address" in clean:
        clean["pickup_address"] = str(clean["pickup_address"])[:255].strip()
    if "description" in clean:
        clean["description"] = str(clean["description"])[:256].strip()
    return clean


def missing_fields(slots: dict, offers: list[dict]) -> list[str]:
    missing = []
    if not slots.get("category"):
        missing.append("任务类型（例如资料打印、奶茶咖啡代取）")
    if not slots.get("pickup_address"):
        missing.append("实际取件或服务地点")
    if not slots.get("delivery_address_id"):
        missing.append("本人地址簿中的送达地址ID（由 Java 校验归属）")
    if not slots.get("description"):
        missing.append("任务需求说明")
    if slots.get("category") and not offers and slots.get("tip") is None:
        missing.append("公开任务小费（可为0，但预付总额必须大于0）")
    if slots.get("category") and not offers and all(
        slots.get(k) in (None, 0) for k in ("tip", "delivery_fee", "product_cost")
    ) and slots.get("tip") is not None:
        missing.append("非零的合法预付费用分项")
    return missing


def _applicable(offer: dict, slots: dict) -> bool:
    if offer.get("category") != slots.get("category"):
        return False
    subtype = slots.get("sub_type")
    if subtype and offer.get("subType") != subtype:
        word = "打印" if subtype == "资料打印" else "咖啡" if subtype == "奶茶咖啡代取" else ""
        if not word or word not in str(offer.get("title", "")):
            return False
    try:
        now = datetime.now(CAMPUS_TZ).replace(tzinfo=None)
        if not (datetime.fromisoformat(offer["availableFrom"]) <= now
                < datetime.fromisoformat(offer["availableUntil"])):
            return False
        price = _currency(offer.get("servicePrice"))
        if price is None:
            return False
        cap = slots.get("max_budget")
        return cap is None or price <= cap
    except (KeyError, ValueError, TypeError):
        return False


def preview_payload(slots: dict, decision: dict) -> dict:
    if decision["mode"] == "offer":
        return {"mode": "SERVICE_OFFER", "offer": {
            "offerId": decision["offer_id"], "pickupAddress": slots["pickup_address"],
            "deliveryAddressId": slots["delivery_address_id"],
            "shoppingBudget": slots.get("shopping_budget", 0),
            "privateNote": None,
        }}
    return {"mode": "PUBLIC_TASK", "task": {
        "type": slots["category"], "subType": slots.get("sub_type"),
        "publicDesc": slots["description"],
        "pickupAddress": slots["pickup_address"],
        "deliveryAddressId": slots["delivery_address_id"],
        "taskSpecs": (f'{{"数量":{slots["pages"]}}}' if slots.get("pages") else None),
        "tip": slots.get("tip"),
        "deliveryFee": slots.get("delivery_fee", 0),
        "productCost": slots.get("product_cost", 0),
        "expireMinutes": 60,
    }}


def build_delegation_graph(checkpointer, extractor: DemandExtractor,
                           java: JavaReadOnlyClient, actor: Actor):
    async def extract(state: DelegationState) -> dict:
        message = next((m.content for m in reversed(state.get("messages", []))
                        if m.type == "human" and isinstance(m.content, str)), "")
        if not isinstance(message, str):
            message = ""
        patch, degraded = await extractor.extract(message, state.get("slots", {}))
        slots = {**state.get("slots", {}), **validate_patch(patch)}
        return {"slots": slots, "turns": state.get("turns", 0) + 1,
                "model_degraded": degraded}

    async def matching(state: DelegationState) -> dict:
        slots = state["slots"]
        offers = []
        if slots.get("category"):
            try:
                raw = await java.get("/internal/agent/v1/offers", actor,
                                     {"category": slots["category"], "page": 1, "size": 10})
                for offer in raw.get("records", []):
                    if _applicable(offer, slots):
                        offers.append({
                            "offerId": int(offer["offerId"]), "title": str(offer["title"])[:80],
                            "category": offer["category"], "subType": offer.get("subType"),
                            "servicePrice": offer["servicePrice"],
                            "availableFrom": offer["availableFrom"], "availableUntil": offer["availableUntil"],
                        })
            except (JavaToolError, AttributeError, ValueError, KeyError, TypeError):
                logger.warning("delegation_offer_lookup_failed")
                # Search outage is not equivalent to 'zero suitable services'.
                return {"status": "lookup_unavailable", "offers": [], "question":
                        "服务橱窗暂时无法核验，请稍后重试。不会自动转成公开任务或产生费用。",
                        "messages": [AIMessage(content="服务橱窗暂时不可用，尚未生成交易。")]}
        required = missing_fields(slots, offers)
        if required:
            q = "还需要确认：" + "；".join(required) + "。预算上限不自动等于小费或商品预算。"
            return {"status": "needs_clarification", "offers": offers, "question": q,
                    "messages": [AIMessage(content=q)]}
        msg = "已查找同校区服务候选。请选择已有服务或公开任务，随后必须在 Java 进行价格预览与支付确认。"
        if not offers:
            msg = "没有找到当前可核验的合适服务；可选择创建公开任务草稿，不会直接发布。"
        return {"status": "choose_mode", "offers": offers, "question": msg,
                "messages": [AIMessage(content=msg)]}

    def next_step(state: DelegationState) -> Literal["gate", "__end__"]:
        return "gate" if state.get("status") == "choose_mode" else "__end__"

    def choice_gate(state: DelegationState) -> dict:
        user_choice = interrupt({
            "kind": "choose_mode", "offers": state.get("offers", []),
            "public_available": True,
            "notice": "这里只决定使用哪一种草稿。真实金额由 Java 重新校验，尚未扣款。",
        })
        if not isinstance(user_choice, dict) or user_choice.get("mode") not in ("public", "offer"):
            return {"status": "invalid_choice", "question": "请选择公开任务或一个已展示的服务。",
                    "messages": [AIMessage(content="选择无效，未创建草稿。")]}
        if user_choice["mode"] == "offer":
            candidates = [x["offerId"] for x in state.get("offers", [])]
            if user_choice.get("offer_id") not in candidates:
                return {"status": "invalid_choice", "question": "服务已失效，请刷新候选。",
                        "messages": [AIMessage(content="服务选择无效，未创建草稿。")]}
        else:
            if state["slots"].get("tip") is None:
                return {"status": "needs_clarification", "question": "公开任务必须明确小费金额。",
                        "messages": [AIMessage(content="请明确公开任务小费，未生成预览。")]}
        proposal = preview_payload(state["slots"], user_choice)
        # This is only form PREFILL. User must call Java POST /api/agent/drafts and /confirm.
        return {"preview_input": proposal, "status": "ready_for_java_preview",
                "question": "已生成预览参数，请在 Java 草稿接口取得真实报价并确认。",
                "messages": [AIMessage(content="预览参数已准备好，尚未创建任务或扣款。")]}

    graph = StateGraph(DelegationState)
    graph.add_node("extract", extract)
    graph.add_node("match", matching)
    graph.add_node("gate", choice_gate)
    graph.add_edge(START, "extract")
    graph.add_edge("extract", "match")
    graph.add_conditional_edges("match", next_step, {"gate": "gate", "__end__": END})
    graph.add_edge("gate", END)
    return graph.compile(checkpointer=checkpointer)
