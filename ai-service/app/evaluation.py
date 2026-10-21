"""Repeatable Stage 6 OFFLINE synthetic ranking vs fixed business-rule baseline.

This tool CANNOT claim improved real-world matching accuracy, user conversion,
model performance or pedestrian ETA. Labels and distances are handcrafted
for deterministic regression. Real reviewer-labeled samples are not included.
"""
import argparse
import json
from datetime import datetime, timedelta
from pathlib import Path
from zoneinfo import ZoneInfo

from app.agent.route_matching import WalkNetwork, rank_cards

ZONE = ZoneInfo("Asia/Shanghai")


def baseline_rank(items, slots, now):
    """Fair comparator: identical Java-eligible shortlist; type/time filter and service-fee sort.
    No routing: baseline must NOT claim a detour estimate.
    """
    result = []
    for card in items:
        try:
            expiry = datetime.fromisoformat(card["expireTime"])
            if expiry.tzinfo is None:
                expiry = expiry.replace(tzinfo=ZONE)
            if expiry.astimezone(ZONE) <= now or expiry.astimezone(ZONE) <= datetime.fromisoformat(
                slots["departure_at"]
            ).astimezone(ZONE):
                continue
            if slots.get("preferred_type") and card.get("type") != slots["preferred_type"]:
                continue
            fee = float(card["tip"]) + float(card["deliveryFee"])
            if fee < 0:
                continue
            result.append((fee, int(card["taskId"])))
        except (KeyError, ValueError, TypeError, OverflowError):
            continue
    return [tid for _, tid in sorted(result, key=lambda row: (-row[0], row[1]))]


def precision_at_k(ids, relevant, k):
    return sum(item in relevant for item in ids[:k]) / k


def evaluate_cases(dataset: dict, *, now: datetime) -> dict:
    assert dataset.get("source_type") == "synthetic_offline_fixture"
    samples = dataset["cases"]
    results = []
    for case in samples:
        dep = now + timedelta(hours=24)
        slots = dict(case["slots"])
        slots["departure_at"] = dep.isoformat()
        tasks = []
        for card in case["cards"]:
            item = dict(card)
            item["expireTime"] = (dep + timedelta(hours=24)).isoformat()
            tasks.append(item)
        network = WalkNetwork(case["network"], int(case["campus_id"]))
        verified, unverified = rank_cards(slots, tasks, network, now=now)
        baseline = baseline_rank(tasks, slots, now)
        labels = set(case["oracle_relevant_ids"])
        got = [card["taskId"] for card in verified]
        withheld = [card["taskId"] for card in unverified]
        # No oracle improvement is claimed when route evidence is missing.
        asserted_safe = set(got)
        assert asserted_safe.isdisjoint(set(case["known_unsafe_ids"])), case["id"]
        assert all(c["estimatedDetourMinutes"] is None and c["score"] is None
                   for c in unverified), case["id"]
        for c in verified:
            assert 0 <= c["estimatedDetourMinutes"] <= slots["max_detour_minutes"]
            assert 0 <= c["score"] <= 100
        has_oracle = case.get("oracle_assessed", True)
        results.append({
            "case": case["id"],
            "oracle_relevant_count": len(labels),
            "verified_recommendations": got,
            "route_unverified": withheld,
            "baseline_ranking": baseline,
            "p_at_2_route": precision_at_k(got, labels, 2) if has_oracle else None,
            "p_at_2_baseline": precision_at_k(baseline, labels, 2) if has_oracle else None,
            "unsafe_claim_count": len(asserted_safe & set(case["known_unsafe_ids"])),
        })
    assessed = [x for x in results if x["p_at_2_route"] is not None]
    return {
        "source_type": "synthetic_offline_fixture",
        "fixture_version": dataset["fixture_version"],
        "cases": results,
        "summary": {
            "cases_total": len(results),
            "cases_with_manual_relevance_labels": len(assessed),
            "unsafe_route_claims": sum(x["unsafe_claim_count"] for x in results),
            "route_precision_at_2_macro": round(sum(x["p_at_2_route"] for x in assessed) / len(assessed), 4),
            "baseline_precision_at_2_macro": round(sum(x["p_at_2_baseline"] for x in assessed) / len(assessed), 4),
        },
        "disclaimer": "Synthetic, manually labeled routes only; not real-user accuracy or measured ETA.",
    }


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--fixture", type=Path, default=Path("evaluation/synthetic_routes.json"))
    parser.add_argument("--output", type=Path)
    args = parser.parse_args()
    output = evaluate_cases(json.loads(args.fixture.read_text(encoding="utf-8")),
                            now=datetime.now(ZONE))
    document = json.dumps(output, ensure_ascii=False, indent=2)
    if args.output:
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(document + "\n", encoding="utf-8")
    print(document)


if __name__ == "__main__":
    main()
