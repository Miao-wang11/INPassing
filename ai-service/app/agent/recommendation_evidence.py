"""Auditable model-authored comparisons. Narrative is accepted only when the
same-turn Java task details and MCP detour/eligibility evidence back its facts.
"""
import math
import re
from datetime import datetime
from decimal import Decimal, InvalidOperation

from app.agent.route_matching import _card, _fee

FIELDS = {"serviceFee", "estimatedDetourMinutes", "type", "pickupArea",
          "deliveryArea", "expireTime"}
TOOL = {
    "serviceFee": "get_task_detail",
    "type": "get_task_detail",
    "pickupArea": "get_task_detail",
    "deliveryArea": "get_task_detail",
    "expireTime": "get_task_detail",
    "estimatedDetourMinutes": "estimate_task_detour",
}
BAD = re.compile(r"(?:全库|全量任务|全局最优|全校最优|保证准时|一定准时|无需排队|肯定不排队|门禁畅通|重量很轻|已核实重量|绝对能接|公里|公斤|千克|\bkg\b)", re.I)
NUM = r"(\d+(?:\.\d+)?)"
FEE = re.compile(r"(?:¥|￥)\s*" + NUM + r"|"+NUM+r"\s*元")
MIN = re.compile(NUM + r"\s*分钟")
TASK = re.compile(r"任务\s*[#＃:]?\s*(\d{1,18})")


def _money(value):
    return Decimal(str(value)).quantize(Decimal("0.01"))


def _same(a, b):
    return abs(float(a) - float(b)) < 0.011


def _facts(tid, looked_up, estimated):
    t = looked_up[tid]
    route = estimated.get(tid) or {}
    return {
        "serviceFee": _fee(t),
        "estimatedDetourMinutes": route.get("estimatedDetourMinutes")
            if route.get("routeVerified") is True else None,
        "type": t.get("type"),
        "pickupArea": t.get("pickupArea"),
        "deliveryArea": t.get("deliveryArea"),
        "expireTime": t.get("expireTime"),
    }


def _sanitize_text(text, refs, seen_ids, slots, *, allow_user_limit=True):
    if not isinstance(text, str) or not text.strip() or len(text) > 300 or BAD.search(text):
        raise ValueError("UNSUPPORTED_EXPLANATION")
    if set(map(int, TASK.findall(text))) - seen_ids:
        raise ValueError("UNSEEN_COMPARISON_TASK")
    fees = [v for ref in refs if ref["field"] == "serviceFee"
            for v in [ref["value"]] if v is not None]
    times = [v for ref in refs if ref["field"] == "estimatedDetourMinutes"
             for v in [ref["value"]] if v is not None]
    if allow_user_limit and slots.get("max_detour_minutes") is not None:
        times.append(slots["max_detour_minutes"])
    for match in FEE.finditer(text):
        amount = float(match.group(1) or match.group(2))
        if not any(_same(amount, v) for v in fees):
            raise ValueError("FABRICATED_REWARD")
    for match in MIN.finditer(text):
        mins = float(match.group(1))
        if not any(_same(mins, v) for v in times):
            raise ValueError("FABRICATED_DETOUR")
    if re.search(r"轻便|很轻|不会排队|不用等|保证|最快|肯定|门禁正常", text):
        raise ValueError("UNVERIFIED_UNKNOWN_CLAIM")
    return text.strip()


def _references(tid, fields, looked_up, estimated):
    if not isinstance(fields, list) or not fields or len(fields) > 6:
        raise ValueError("MISSING_EVIDENCE_REFS")
    facts = _facts(tid, looked_up, estimated)
    result = []
    for field in fields:
        if field not in FIELDS or facts[field] is None:
            raise ValueError("INVALID_EVIDENCE_FIELD")
        result.append({"taskId": tid, "tool": TOOL[field], "field": field,
                       "value": facts[field]})
    return result


def _confirm_expiry(tid, slots, looked_up):
    """Java expireTime is a claim cutoff, not a delivery deadline."""
    from zoneinfo import ZoneInfo
    zone = ZoneInfo("Asia/Shanghai")
    departure = slots.get("departure_at")
    if not departure:
        raise ValueError("DEPARTURE_UNCONFIRMED")
    try:
        expiry = datetime.fromisoformat(str(looked_up[tid]["expireTime"]))
        expiry = expiry.replace(tzinfo=zone) if expiry.tzinfo is None else expiry.astimezone(zone)
        departure_at = datetime.fromisoformat(departure).astimezone(zone)
        if expiry <= departure_at or expiry <= datetime.now(zone):
            raise ValueError("TASK_EXPIRES_BEFORE_DEPARTURE")
    except (KeyError, TypeError):
        raise ValueError("INVALID_EXPIRY") from None


def _check_comparatives(summary, recommended, excluded, looked_up, estimates):
    """Guard directional claims, not merely the numeric tokens used to state them."""
    if len(recommended) != 1:
        return
    selected = recommended[0]
    baseline = _facts(selected, looked_up, estimates)
    for tid in excluded:
        facts = _facts(tid, looked_up, estimates)
        for segment in re.split(r"[。；;]", summary):
            if not re.search(r"任务\s*[#＃:]?\s*" + str(tid) + r"(?!\d)", segment):
                continue
            if "更高" in segment and (
                facts["serviceFee"] is None or baseline["serviceFee"] is None or
                facts["serviceFee"] <= baseline["serviceFee"]
            ):
                raise ValueError("FALSE_COMPARATIVE")
            if "更短" in segment and (
                facts["estimatedDetourMinutes"] is None or
                baseline["estimatedDetourMinutes"] is None or
                facts["estimatedDetourMinutes"] >= baseline["estimatedDetourMinutes"]
            ):
                raise ValueError("FALSE_COMPARATIVE")


def validate_recommendation(payload, *, seen, looked_up, eligible, estimates, slots):
    ids = payload.get("task_ids", [])
    if not ids or len(ids) != len(set(ids)) or len(ids) > 3:
        raise ValueError("INVALID_RECOMMENDATION")
    chosen = []
    for tid in ids:
        if tid not in seen or tid not in looked_up or eligible.get(tid) is not True or tid not in estimates:
            raise ValueError("UNVERIFIED_TASK_ID")
        route = estimates[tid]
        minutes = route.get("estimatedDetourMinutes")
        cap = slots.get("max_detour_minutes")
        if cap is not None and route.get("routeVerified") is not True:
            raise ValueError("ROUTE_UNVERIFIED_FOR_LIMIT")
        if route.get("routeVerified") and (not isinstance(minutes, (int, float))
            or not math.isfinite(minutes) or minutes < 0 or
            (cap is not None and minutes > cap)):
            raise ValueError("DETOUR_LIMIT")
        required_type = slots.get("required_type")
        if required_type and looked_up[tid].get("type") != required_type:
            raise ValueError("TASK_TYPE_CONSTRAINT")
        _confirm_expiry(tid, slots, looked_up)
        chosen.append(_card(looked_up[tid], minutes if route.get("routeVerified") else None,
                            None, "工具核实候选（解释将在下方提供）"))

    proposals = payload.get("recommendations")
    if proposals is None:
        # Old scripted model is supported but NEVER presented as model-grounded explanation.
        return {"verified": [c for c in chosen if c["routeVerified"]],
                "unverified": [c for c in chosen if not c["routeVerified"]],
                "comparison": [], "summary": "候选已按任务详情、资格及路线工具核验；没有收到可验证的模型比较说明。",
                "explanation_degraded": True}
    if not isinstance(proposals, list) or [p.get("task_id") for p in proposals] != ids:
        raise ValueError("EXPLANATION_ID_ORDER")
    refs_all = []
    explanations = []
    known_ids = set(looked_up)
    for obj, card in zip(proposals, chosen):
        tid = obj["task_id"]
        refs = _references(tid, obj.get("evidence"), looked_up, estimates)
        reason = _sanitize_text(obj.get("reason"), refs, known_ids, slots)
        card["reason"] = reason
        card["evidence"] = refs
        card["unknowns"] = ["重量", "排队与交接等待时间"]
        refs_all.extend(refs)
        explanations.append({"taskId": tid, "kind": "recommended", "reason": reason,
                             "evidence": refs})
    excluded = payload.get("excluded", [])
    if not isinstance(excluded, list) or len(excluded) > 3:
        raise ValueError("INVALID_EXCLUSIONS")
    for obj in excluded:
        tid = obj.get("task_id")
        if tid not in seen or tid in ids or tid not in looked_up or tid not in estimates:
            raise ValueError("UNVERIFIED_EXCLUSION")
        refs = _references(tid, obj.get("evidence"), looked_up, estimates)
        _confirm_expiry(tid, slots, looked_up)
        reason = _sanitize_text(obj.get("reason"), refs, known_ids, slots)
        refs_all.extend(refs)
        explanations.append({"taskId": tid, "kind": "excluded", "reason": reason,
                             "evidence": refs})
    summary = _sanitize_text(payload.get("summary"), refs_all, known_ids, slots)
    # No unsupported claims of optimality based on partial pagination.
    if re.search(r"最合适|最好的任务|唯一的任务", summary):
        raise ValueError("GLOBAL_OPTIMALITY_NOT_PROVEN")
    _check_comparatives(summary, ids, [x["task_id"] for x in excluded],
                        looked_up, estimates)
    return {"verified": [c for c in chosen if c["routeVerified"]],
            "unverified": [c for c in chosen if not c["routeVerified"]],
            "comparison": explanations, "summary": summary,
            "explanation_degraded": False}
