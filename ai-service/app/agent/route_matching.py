"""ShunShou Phase 4: conservative, explainable commute task matching.

No made-up campus GPS or walking times. Only admin-verified directed pedestrian
network edges are routable; missing edges/locations are explicitly UNVERIFIED.
No Python write tools. Explicit selection yields ONLY a Java JWT accept endpoint;
TaskOrderService.acceptOrder() has the final business/concurrency authority.
"""
import heapq
import json
import logging
import math
import re
from datetime import datetime, timedelta
from typing import Annotated
from zoneinfo import ZoneInfo

from langchain_core.messages import AIMessage, AnyMessage
from langgraph.graph import START, END, StateGraph
from langgraph.graph.message import add_messages
from langgraph.types import interrupt
from pydantic import BaseModel, Field
from typing_extensions import TypedDict

from app.clients.java_backend import JavaToolError

logger = logging.getLogger(__name__)
ZONE = ZoneInfo("Asia/Shanghai")
TYPES = ("代取快递", "代拿餐食", "校内代办", "代购物品", "通用代办")
WALK_SPEED_METERS_PER_SECOND = 1.25  # explicitly labelled planning assumption
HANDOFF_BUFFER_MINUTES = 4.0  # planning allowance, NOT measured service duration
MAX_FETCH_PAGES = 2
PAGE_SIZE = 20


class RoutePatch(BaseModel):
    origin: str | None = Field(default=None, max_length=100)
    destination: str | None = Field(default=None, max_length=100)
    departure_at: str | None = None
    max_detour_minutes: int | None = Field(default=None, ge=1, le=90)
    preferred_type: str | None = None


class RouteState(TypedDict, total=False):
    messages: Annotated[list[AnyMessage], add_messages]
    slots: dict
    status: str
    answer: str
    verified: list[dict]
    unverified: list[dict]
    selected: dict | None
    model_degraded: bool
    turns: int
    limitations: list[str]


def _number(value: str) -> int | None:
    if value.isdigit():
        return int(value)
    digits = {c: i for i, c in enumerate("零一二三四五六七八九")}
    if value in digits:
        return digits[value]
    if value == "十":
        return 10
    if "十" in value:
        left, _, right = value.partition("十")
        if (not left or left in digits) and (not right or right in digits):
            return digits.get(left, 1) * 10 + digits.get(right, 0)
    return None


def _normalize(raw: str) -> str:
    return re.sub(r"[\s\-（）()，,。]", "", raw).strip().lower()


def _parse_departure(message: str, now: datetime) -> str | None:
    # Only explicit, interpretable day+clock combinations; no silent next-day guess.
    m = re.search(r"(?:(今天|明天|后天))?\s*(凌晨|早上|上午|中午|下午|晚上)?"
                  r"([零一二三四五六七八九十]{1,3}|\d{1,2})点(?:半|([0-5]\d)分?)?", message)
    if not m:
        return None
    day, period, hour_token, minutes = m.groups()
    hour = _number(hour_token)
    if hour is None or hour > 23:
        return None
    if period in ("下午", "晚上") and 1 <= hour <= 11:
        hour += 12
    if period == "中午" and 1 <= hour <= 11:
        hour += 12
    if period in ("早上", "上午", "凌晨") and hour > 12:
        return None
    minute = 30 if "点半" in m.group(0) else int(minutes or 0)
    if minute > 59:
        return None
    offset = {"今天": 0, "明天": 1, "后天": 2}.get(day, 0)
    value = now.replace(hour=hour, minute=minute, second=0, microsecond=0) + timedelta(days=offset)
    if value <= now:
        return None
    return value.isoformat()


def _rule_extract(message: str, now: datetime | None = None) -> dict:
    now = now or datetime.now(ZONE)
    result = {}
    # Directly expressed route only; ambiguous locations are not auto-resolved.
    place = re.search(r"从\s*([^，。;；]{2,70}?)\s*(?:回|去|到|前往)\s*([^，。;；]{2,70}?)(?=，|。|;|；|$)", message)
    if place:
        result["origin"] = place.group(1).strip()
        result["destination"] = place.group(2).strip()
    elif "出发地" in message or "目的地" in message:
        start = re.search(r"出发地[：:]\s*([^，。;；]{1,60})", message)
        end = re.search(r"目的地[：:]\s*([^，。;；]{1,60})", message)
        if start:
            result["origin"] = start.group(1).strip()
        if end:
            result["destination"] = end.group(1).strip()
    detour = re.search(r"(?:最多绕路|最大绕路|绕路不超过|最多绕行)\s*"
                      r"([零一二三四五六七八九十]{1,3}|\d{1,2})\s*分钟", message)
    if detour:
        value = _number(detour.group(1))
        if value and 1 <= value <= 90:
            result["max_detour_minutes"] = value
    departure = _parse_departure(message, now)
    if departure:
        result["departure_at"] = departure
    for type_name in TYPES:
        if type_name in message and ("只接" in message or "优先" in message or "只想接" in message):
            result["preferred_type"] = type_name
            break
    return result


class RouteExtractor:
    def __init__(self, settings):
        self.model = None
        if settings.model_provider == "openai_compatible":
            from langchain_openai import ChatOpenAI
            self.model = ChatOpenAI(model=settings.model_name, api_key=settings.model_api_key,
                                    base_url=settings.model_base_url,
                                    request_timeout=settings.request_timeout_seconds,
                                    max_retries=0, temperature=0).with_structured_output(
                                        RoutePatch, method="json_schema")

    async def extract(self, message: str, current: dict) -> tuple[dict, bool]:
        now = datetime.now(ZONE)
        baseline = _rule_extract(message, now)
        if not self.model:
            return baseline, False
        try:
            output = await self.model.ainvoke([
                ("system", "只从用户明确表达提取校内出发地点、目的地点、出发时间、最多绕路分钟及偏好任务类型。"
                 "不编造GPS、路程、耗时、地点ID、任务ID。时间必须是带Asia/Shanghai时区的未来ISO时间，"
                 "不确定则为null。任务类型仅限代取快递、代拿餐食、校内代办、代购物品、通用代办。"
                 "用户ID和校区ID不可提取。"),
                ("human", f"现有非敏感字段：{current}\n新消息：{message}"),
            ])
            patch = output.model_dump(exclude_none=True) if isinstance(output, RoutePatch) else {}
            for k in ("origin", "destination"):
                if isinstance(patch.get(k), str) and patch[k].strip():
                    baseline[k] = patch[k].strip()
            # Avoid model overriding max-detour/time constraints explicitly parsed by rules.
            if "max_detour_minutes" not in baseline and type(patch.get("max_detour_minutes")) is int:
                baseline["max_detour_minutes"] = patch["max_detour_minutes"]
            if "departure_at" not in baseline and isinstance(patch.get("departure_at"), str):
                baseline["departure_at"] = patch["departure_at"]
            if "preferred_type" not in baseline and patch.get("preferred_type") in TYPES:
                baseline["preferred_type"] = patch["preferred_type"]
            return baseline, False
        except Exception:
            logger.warning("route_extraction_model_unavailable")
            return baseline, True


def _valid_slots(patch: dict) -> dict:
    result = {}
    for key in ("origin", "destination"):
        if isinstance(patch.get(key), str) and 1 <= len(patch[key].strip()) <= 100:
            result[key] = patch[key].strip()
    n = patch.get("max_detour_minutes")
    if type(n) is int and 1 <= n <= 90:
        result["max_detour_minutes"] = n
    if patch.get("preferred_type") in TYPES:
        result["preferred_type"] = patch["preferred_type"]
    t = patch.get("departure_at")
    if isinstance(t, str):
        try:
            dt = datetime.fromisoformat(t)
            if dt.tzinfo is not None and dt.utcoffset() is not None:
                dt = dt.astimezone(ZONE)
                if datetime.now(ZONE) < dt < datetime.now(ZONE) + timedelta(days=8):
                    result["departure_at"] = dt.isoformat()
        except ValueError:
            pass
    return result


class WalkNetwork:
    """Minimal trusted directed graph: never treats missing edges as reachable."""
    def __init__(self, data: dict, trusted_campus: int):
        self.labels: dict[str, int] = {}
        self.adjacency: dict[int, list[tuple[int, float]]] = {}
        self.valid = False
        if not isinstance(data, dict) or data.get("campusId") != trusted_campus:
            return
        places = data.get("places", [])
        edges = data.get("edges", [])
        if not isinstance(places, list) or not isinstance(edges, list) or len(places) > 200 or len(edges) > 1000:
            return
        nodes = set()
        aliases: dict[str, set[int]] = {}
        for p in places:
            try:
                i = int(p["id"])
                if i <= 0 or not p.get("verifiedAt") or not p.get("name"):
                    continue
                strings = [p["name"]]
                other = p.get("aliasesJson", "[]")
                other = json.loads(other) if isinstance(other, str) else other
                if isinstance(other, list):
                    strings.extend(a for a in other if isinstance(a, str) and len(a) <= 100)
                nodes.add(i)
                for alias in strings:
                    key = _normalize(alias)
                    if key:
                        aliases.setdefault(key, set()).add(i)
            except (KeyError, ValueError, TypeError, json.JSONDecodeError):
                continue
        self.labels = {k: next(iter(ids)) for k, ids in aliases.items() if len(ids) == 1}
        for e in edges:
            try:
                a, b = int(e["from"]), int(e["to"])
                d = float(e["distanceMeters"])
                if (a not in nodes or b not in nodes or a == b or not e.get("verifiedAt")
                        or not math.isfinite(d) or not 0 < d <= 100000):
                    continue
                self.adjacency.setdefault(a, []).append((b, d))
            except (ValueError, TypeError, KeyError):
                continue
        self.valid = bool(self.labels and self.adjacency)

    def place(self, label: str) -> int | None:
        return self.labels.get(_normalize(label))

    def meters(self, start: int, end: int) -> float | None:
        if start == end:
            return 0.0
        pq = [(0.0, start)]
        best = {start: 0.0}
        while pq:
            distance, node = heapq.heappop(pq)
            if node == end:
                return distance
            if distance > best[node]:
                continue
            for nxt, weight in self.adjacency.get(node, []):
                candidate = distance + weight
                if candidate < best.get(nxt, float("inf")):
                    best[nxt] = candidate
                    heapq.heappush(pq, (candidate, nxt))
        return None

    def detour(self, start: str, end: str, pickup: str, delivery: str) -> float | None:
        keys = [self.place(x) for x in (start, pickup, delivery, end)]
        if not self.valid or any(v is None for v in keys):
            return None
        original = self.meters(keys[0], keys[3])
        segments = [self.meters(a, b) for a, b in zip(keys, keys[1:])]
        if original is None or any(d is None for d in segments):
            return None
        extra_walk = max(0.0, sum(segments) - original)
        return extra_walk / WALK_SPEED_METERS_PER_SECOND / 60 + HANDOFF_BUFFER_MINUTES


def _fee(card: dict) -> float | None:
    try:
        service = float(card.get("tip") or 0) + float(card.get("deliveryFee") or 0)
        if not math.isfinite(service) or service < 0:
            return None
        return service
    except (ValueError, TypeError):
        return None


def _card(card: dict, detour: float | None, score: float | None, reason: str) -> dict:
    return {
        "taskId": int(card["taskId"]), "type": card.get("type"), "subType": card.get("subType"),
        "publicDesc": str(card.get("publicDesc") or "")[:256],
        "pickupArea": str(card.get("pickupArea") or "")[:100],
        "deliveryArea": str(card.get("deliveryArea") or "")[:100],
        "serviceFee": _fee(card), "prepaidTotal": card.get("reward"),
        "acceptExpiresAt": card.get("expireTime"),
        "estimatedDetourMinutes": round(detour, 1) if detour is not None else None,
        "score": round(score, 1) if score is not None else None,
        "routeVerified": detour is not None,
        "reason": reason, "requiresFinalJavaCheck": True,
        "acceptEndpoint": f'/agent/matches/accept/{int(card["taskId"])}',
    }


def rank_cards(slots: dict, items: list[dict], network: WalkNetwork, *, now: datetime | None = None):
    now = now or datetime.now(ZONE)
    end_at = datetime.fromisoformat(slots["departure_at"]).astimezone(ZONE)
    verified, unverified = [], []
    seen = set()
    for t in items:
        try:
            tid = int(t["taskId"])
            if tid <= 0 or tid in seen:
                continue
            seen.add(tid)
            expiry = datetime.fromisoformat(t["expireTime"])
            expiry = expiry.replace(tzinfo=ZONE) if expiry.tzinfo is None else expiry.astimezone(ZONE)
            if expiry <= now or expiry <= end_at:
                continue
            if slots.get("preferred_type") and t.get("type") != slots["preferred_type"]:
                continue
            fee = _fee(t)
            if fee is None:
                continue
            travel = network.detour(slots["origin"], slots["destination"],
                                    str(t.get("pickupArea") or ""), str(t.get("deliveryArea") or ""))
            if travel is None:
                unverified.append(_card(t, None, None,
                    "地点或步行路网未核实，无法证明符合绕路上限；请先自行核实路线和任务重量"))
                continue
            if travel > slots["max_detour_minutes"]:
                continue
            # Transparent fixed formula: route effort matters more than reward.
            route_score = 65 * (1 - travel / slots["max_detour_minutes"])
            fee_score = 25 * min(fee / 10.0, 1)
            preference = 10 if slots.get("preferred_type") else 5
            score = route_score + fee_score + preference
            verified.append(_card(t, travel, score,
                "基于经核实步行路段估算绕路，含假设的4分钟交接时间；"
                "服务费为小费+配送费，不含商品预算；不是准时保证"))
        except (KeyError, TypeError, ValueError, OverflowError):
            continue
    verified.sort(key=lambda x: (-x["score"], x["taskId"]))
    unverified.sort(key=lambda x: (-float(x["serviceFee"]), x["taskId"]))
    return verified[:20], unverified[:20]


def build_route_graph(checkpointer, extractor, java, actor):
    async def parse(state: RouteState) -> dict:
        message = next((m.content for m in reversed(state.get("messages", []))
                        if m.type == "human" and isinstance(m.content, str)), "")
        patch, degraded = await extractor.extract(message, state.get("slots", {}))
        slots = {**state.get("slots", {}), **_valid_slots(patch)}
        return {"slots": slots, "model_degraded": degraded, "turns": state.get("turns", 0) + 1}

    async def fetch_rank(state: RouteState) -> dict:
        slots = state["slots"]
        missing = []
        for field, prompt in (("origin", "出发地点"), ("destination", "目的地点"),
                              ("departure_at", "明确的未来出发日期和时间"),
                              ("max_detour_minutes", "最大绕路分钟数")):
            if not slots.get(field):
                missing.append(prompt)
        if missing:
            answer = "需要确认：" + "、".join(missing) + "。"
            return {"status": "needs_clarification", "answer": answer,
                    "messages": [AIMessage(content=answer)]}
        batch = []
        try:
            for page in range(1, MAX_FETCH_PAGES + 1):
                data = await java.get("/internal/agent/v1/matching/candidates", actor,
                                      {"page": page, "size": PAGE_SIZE})
                if not isinstance(data, dict) or not isinstance(data.get("records"), list):
                    raise ValueError("Malformed candidate batch")
                batch.extend(data["records"])
                if not data.get("morePages"):
                    break
        except (JavaToolError, ValueError, TypeError):
            return {"status": "lookup_unavailable", "answer": "暂时无法验证候选任务及接单资格。",
                    "messages": [AIMessage(content="任务查询暂时不可用，请稍后重试。")]}
        limitations = ["所有候选只做接单资格预检；最终抢单以 Java 原有 Service 实时判断为准。",
                       "任务 expireTime 表示接单截止，不代表必须完成配送的时限。",
                       "任务重量未提供可信结构化数据，不保证适合搬运。",
                       "步行时间按经核实路段长度和假设速度1.25米/秒估算，另加假设4分钟交接时间。"]
        try:
            raw = await java.get("/internal/agent/v1/matching/network", actor)
            network = WalkNetwork(raw, actor.campus_id)
        except (JavaToolError, ValueError, TypeError):
            network = WalkNetwork({}, actor.campus_id)
            limitations.append("校区步行网络不可用，禁止生成绕路时间。")
        verified, unverified = rank_cards(slots, batch, network)
        if not network.valid:
            limitations.append("当前校区没有可计算的经核实完整步行路网，全部候选标为路线未核实。")
        if not verified and not unverified:
            return {"status": "no_candidates", "answer": "没有符合接单资格及当前时间要求的公开任务。",
                    "verified": [], "unverified": [], "limitations": limitations,
                    "messages": [AIMessage(content="没有可推荐的候选任务。")]}
        answer = f"找到{len(verified)}个可估算绕路的候选和{len(unverified)}个路线待核实候选。"
        return {"status": "choose_task", "answer": answer, "verified": verified,
                "unverified": unverified, "limitations": limitations,
                "messages": [AIMessage(content=answer)]}

    def next_step(state: RouteState) -> str:
        return "choose" if state.get("status") == "choose_task" else "__end__"

    def choose(state: RouteState) -> dict:
        choice = interrupt({"kind": "choose_task", "verified": state.get("verified", []),
                            "unverified": state.get("unverified", []),
                            "notice": "仅选择任务，不会自动抢单；路线未核实候选须额外确认风险。"})
        if not isinstance(choice, dict) or type(choice.get("task_id")) is not int:
            return {"status": "invalid_choice", "answer": "请从本轮任务卡片选择任务。"}
        verified = {t["taskId"]: t for t in state.get("verified", [])}
        uncertain = {t["taskId"]: t for t in state.get("unverified", [])}
        selected = verified.get(choice["task_id"])
        if selected is None:
            selected = uncertain.get(choice["task_id"])
            if selected is None or choice.get("acknowledge_unverified") is not True:
                return {"status": "invalid_choice",
                        "answer": "候选不存在或尚未明确确认路线无法核实的风险。"}
        return {"status": "ready_for_java_accept",
                "selected": selected,
                "answer": "已选择任务，但尚未抢单。用户明确点击确认后，使用原 JWT 向 Java POST "
                          + selected["acceptEndpoint"] +
                          "；若任务被抢走，应提示刷新任务推荐，绝不伪造成功订单。"}

    builder = StateGraph(RouteState)
    builder.add_node("parse", parse)
    builder.add_node("rank", fetch_rank)
    builder.add_node("choose", choose)
    builder.add_edge(START, "parse")
    builder.add_edge("parse", "rank")
    builder.add_conditional_edges("rank", next_step, {"choose": "choose", "__end__": END})
    builder.add_edge("choose", END)
    return builder.compile(checkpointer=checkpointer)
