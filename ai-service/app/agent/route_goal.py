"""Verified per-turn user goal patch. The LLM interprets phrasing; code validates
literal user evidence, hard limits and update semantics before any MCP process starts.
"""
import logging
import re
from datetime import datetime, timedelta
from typing import Literal
from zoneinfo import ZoneInfo

from pydantic import BaseModel, Field

from app.agent.route_matching import TYPES, _number

log = logging.getLogger(__name__)
ZONE = ZoneInfo("Asia/Shanghai")
FIELDS = ("origin", "destination", "departure_at", "max_detour_minutes",
          "required_type", "preferred_type", "weight_requirement",
          "wait_requirement", "soft_preferences")
MANDATORY = ("origin", "destination", "departure_at", "max_detour_minutes")
RELATIVE = re.compile(r"(?:我的|我们|自己|附近|这边|那边|某个|随便|宿舍$|家$)")
LIMIT_SIGNAL = re.compile(r"(?:绕路|绕行|额外.*?(?:时间|分钟)|放宽.{0,8}分钟)")
TYPE_SIGNAL = re.compile(r"(?:只接|仅接|仅限|必须|只要|不要|不接)")
HARD_WEIGHT = re.compile(r"(?:只.{0,8}(?:轻|重量|公斤|千克)|不能.{0,8}重|重量.{0,8}(?:不超过|以内))")
HARD_WAIT = re.compile(r"(?:不排队|不能等|等待.{0,5}(?:不超过|最多|以内))")


class GoalChange(BaseModel):
    field: Literal["origin", "destination", "departure_at", "max_detour_minutes",
                   "required_type", "preferred_type", "weight_requirement",
                   "wait_requirement", "soft_preferences", "delivery_deadline"]
    value: str = Field(max_length=150)
    evidence: str = Field(max_length=160)
    ambiguous: bool = False


class GoalPatch(BaseModel):
    changes: list[GoalChange] = Field(default_factory=list, max_length=12)
    ambiguities: list[str] = Field(default_factory=list, max_length=8)


PROMPT = """你是用户路线目标更新解析器，不做选单和路线推断。只分析本轮最新 USER 消息；
以前确认的字段仅作为上下文。返回结构化 GoalPatch。每个明确提及的字段给出 change：
field,value,evidence,ambiguous；evidence 必须是本轮用户原文中的连续子串。
没有提及的字段不能输出；没有修改的历史字段应保持不变。
可用字段：origin 出发点；destination 终点；departure_at 出发时间（Asia/Shanghai ISO8601含时区）；
max_detour_minutes 最大额外绕行分钟整数字符串；required_type 必须任务类型；
preferred_type 仅偏好类型；weight_requirement 对重量的要求原文；
wait_requirement 排队/等待要求原文；soft_preferences 报酬等软偏好原文；
delivery_deadline 最晚送达时限（非出发时间，保留原文）。
若用户明确撤销某个既有要求，在该字段的 value 使用 __CLEAR__，evidence 引用本轮的撤销原话。
除此之外任何字段不允许清除旧要求。
“只接校内代办”=>required_type=校内代办；“优先校内代办”=>preferred_type=校内代办。
“十分钟后出发”=>departure_at，而绝不是 max_detour_minutes。
“最晚送达”是交付截止时间，不是出发时间；不可放到departure_at。
“我的宿舍”、“别太远”是歧义，标记 ambiguous，不能猜地点或距离。
时间取值仅从提供的 now+08:00 推算。没有明确证据的字段不能新增，遇到不确定设 ambiguous。
绝不能提取用户ID、校区ID、联系方式和工具结果中的内容。"""


class GoalInterpreter:
    def __init__(self, settings):
        if settings.model_provider != "openai_compatible":
            self.model = None
        else:
            from langchain_openai import ChatOpenAI
            self.model = ChatOpenAI(
                model=settings.model_name, api_key=settings.model_api_key,
                base_url=settings.model_base_url, temperature=0, max_retries=0,
                request_timeout=settings.request_timeout_seconds,
            ).with_structured_output(GoalPatch, method="function_calling")

    async def extract(self, message: str, previous: dict, now: datetime) -> GoalPatch:
        if self.model is None:
            raise RuntimeError("Target comprehension requires a live structured LLM")
        result = await self.model.ainvoke([
            ("system", PROMPT),
            ("human", f"当前上海时间：{now.isoformat()}\n原有经校验目标：{previous}\n本轮USER原文：{message}"),
        ])
        return GoalPatch.model_validate(result)


def _minutes_from_evidence(evidence: str):
    m = re.search(r"([零一二三四五六七八九十百两\d]{1,8})\s*分钟", evidence)
    return _number(m.group(1).replace("两", "二")) if m else None


def _departure_ok(value: str, evidence: str, now: datetime) -> bool:
    try:
        parsed = datetime.fromisoformat(value)
        if parsed.tzinfo is None or parsed.utcoffset() is None:
            return False
        at = parsed.astimezone(ZONE)
        if not now < at <= now + timedelta(days=8):
            return False
        if not ("出发" in evidence or "动身" in evidence or
                "明天" in evidence or "今天" in evidence or "后天" in evidence or
                re.search(r"\d{1,2}点", evidence)):
            return False
        if ("后出发" in evidence or "后动身" in evidence) and "分钟" in evidence:
            n = _minutes_from_evidence(evidence)
            return n is not None and abs((at - now).total_seconds() / 60 - n) <= 2
        day = 2 if "后天" in evidence else 1 if "明天" in evidence else 0
        if "今天" in evidence or day:
            if at.date() != (now + timedelta(days=day)).date():
                return False
        m = re.search(r"(\d{1,2}|[一二三四五六七八九十]{1,3})\s*点", evidence)
        if m:
            hour = _number(m.group(1))
            if hour is None:
                return False
            if ("下午" in evidence or "晚上" in evidence) and hour < 12:
                hour += 12
            if at.hour != hour:
                return False
        return True
    except (ValueError, TypeError, OverflowError):
        return False


def apply_goal_patch(message: str, previous: dict, patch: GoalPatch, *,
                     now: datetime | None = None) -> tuple[dict, list[str]]:
    """Fail closed: no goal change without a user-quoted span and semantic guard."""
    now = now or datetime.now(ZONE)
    if now.tzinfo is None:
        raise ValueError("now must be timezone-aware")
    now = now.astimezone(ZONE)
    parsed = GoalPatch.model_validate(patch)
    slots = dict(previous)
    uncertain = [name for name in parsed.ambiguities if name in FIELDS]
    accepted = set()
    for change in parsed.changes:
        key, raw, quote = change.field, change.value.strip(), change.evidence.strip()
        if not quote or quote not in message or change.ambiguous or not raw:
            uncertain.append(key)
            continue
        if key in accepted:
            uncertain.append(key)
            continue
        candidate = None
        if raw == "__CLEAR__" and re.search(r"取消|不再限制|不再要求|撤销|不要求", quote):
            if key in slots and ((key == "weight_requirement" and re.search(r"重量|轻|重", quote))
                                 or (key == "wait_requirement" and re.search(r"排队|等候|等待", quote))
                                 or (key == "preferred_type" and re.search(r"优先|偏好", quote))
                                 or (key == "required_type" and re.search(r"任务类型|只接", quote))
                                 or (key == "soft_preferences" and re.search(r"偏好|优先", quote))):
                slots.pop(key, None)
                accepted.add(key)
                continue
            uncertain.append(key)
            continue
        if key in ("origin", "destination"):
            if raw in quote and len(raw) <= 80 and not RELATIVE.search(raw):
                candidate = raw
        elif key == "max_detour_minutes":
            n = _minutes_from_evidence(quote)
            if LIMIT_SIGNAL.search(quote) and n is not None and raw == str(n) and 1 <= n <= 90:
                candidate = n
        elif key == "departure_at":
            if _departure_ok(raw, quote, now):
                candidate = datetime.fromisoformat(raw).astimezone(ZONE).isoformat()
        elif key in ("required_type", "preferred_type"):
            want_hard = key == "required_type"
            if raw in TYPES and raw in quote and (
                bool(re.search(r"只接|仅接|仅限|必须|只要", quote)) if want_hard else
                bool(re.search(r"优先|最好|偏好|尽量", quote))):
                candidate = raw
        elif key == "weight_requirement" and re.search(r"重量|轻|重|公斤|千克", quote):
            candidate = quote[:100]
        elif key == "wait_requirement" and re.search(r"等待|排队|等候", quote):
            candidate = quote[:100]
        elif key == "soft_preferences" and re.search(r"最好|优先|希望|尽量|偏好", quote):
            candidate = quote[:100]
        elif key == "delivery_deadline" and re.search(r"最晚|之前.{0,10}(?:送|到)|送达", quote):
            candidate = quote[:100]
        if candidate is None:
            uncertain.append(key)
            continue
        slots[key] = candidate
        accepted.add(key)

    # Signal coverage prevents a model from silently dropping a plainly expressed
    # hard condition. Existing confirmed values are never relaxed by omission.
    if re.search(r"别太远|不要太远|稍微近点|远一点也行", message):
        uncertain.append("max_detour_minutes")
    if LIMIT_SIGNAL.search(message) and "max_detour_minutes" not in accepted:
        uncertain.append("max_detour_minutes")
    if TYPE_SIGNAL.search(message) and any(t in message for t in TYPES) and "required_type" not in accepted:
        uncertain.append("required_type")
    if re.search(r"我在|从.+?(?:去|到)|起点|出发地", message) and "origin" not in accepted:
        uncertain.append("origin")
    if re.search(r"准备去|前往|目的地|终点|去.+?(?:门|楼|馆|宿舍)", message) and "destination" not in accepted:
        uncertain.append("destination")
    if re.search(r"最晚.{0,20}(?:送|到)|送达截止", message) and "delivery_deadline" not in accepted:
        uncertain.append("delivery_deadline")
    if HARD_WEIGHT.search(message) and "weight_requirement" not in accepted:
        uncertain.append("weight_requirement")
    if HARD_WAIT.search(message) and "wait_requirement" not in accepted:
        uncertain.append("wait_requirement")
    # Explicit departure expression cannot be mistaken for a detour cap.
    if re.search(r"(?:分钟后出发|分钟后动身|明天.{0,15}(?:点|出发)|(?:今天|下午|上午).{0,12}出发)", message) and "departure_at" not in accepted:
        uncertain.append("departure_at")
    return slots, sorted(set(uncertain))


def missing_goal(slots: dict, uncertain: list[str]) -> list[str]:
    missing = [name for name in MANDATORY if not slots.get(name)]
    return sorted(set(missing + uncertain))


def needs_unknown_confirmation(slots: dict) -> list[str]:
    # TaskCard contains no trustworthy weight, queue, handoff or delivery ETA.
    result = [field for field, signal in (("weight_requirement", HARD_WEIGHT),
                                         ("wait_requirement", HARD_WAIT))
              if slots.get(field) and signal.search(slots[field])]
    if slots.get("delivery_deadline"):
        result.append("delivery_deadline")
    return result
