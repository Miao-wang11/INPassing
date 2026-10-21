"""Reproducible offline recommender evaluation; no fabricated live campus evidence.

Inputs must be user-reviewed JSON fixtures. Metrics do not measure LLM accuracy.
All recommendations go through the same existing production ranking function.
"""
import json
from pathlib import Path
from typing import Any

from app.agent.route_matching import WalkNetwork, rank_cards


def precision_at_k(ids: list[int], relevant: set[int], k: int) -> float:
    if k <= 0:
        raise ValueError("k must be positive")
    return len(set(ids[:k]) & relevant) / k


def recall_at_k(ids: list[int], relevant: set[int], k: int) -> float | None:
    return len(set(ids[:k]) & relevant) / len(relevant) if relevant else None


def evaluate_cases(cases: list[dict[str, Any]], k: int = 3) -> dict:
    if not cases:
        raise ValueError("No labeled cases")
    output = []
    for case in cases:
        if not {"campusId", "network", "slots", "candidates", "relevantTaskIds"} <= case.keys():
            raise ValueError("Incomplete evaluation case")
        relevant = set(case["relevantTaskIds"])
        network = WalkNetwork(case["network"], case["campusId"])
        verified, unknown = rank_cards(case["slots"], case["candidates"], network)
        # Baseline: public tasks, same chosen type when specified, sorted by service fee.
        # These fixtures must be PRE-FILTERED by Java eligibility in both methods.
        preferred = case["slots"].get("preferred_type")
        baseline = [
            card for card in case["candidates"]
            if (not preferred or card.get("type") == preferred)
            and int(card["taskId"]) > 0
        ]
        baseline.sort(key=lambda x: (
            -(float(x.get("tip") or 0) + float(x.get("deliveryFee") or 0)),
            int(x["taskId"]),
        ))
        ranked_ids = [v["taskId"] for v in verified]
        baseline_ids = [v["taskId"] for v in baseline]
        output.append({
            "caseId": case["caseId"],
            "baseline": {
                "ids": baseline_ids[:k],
                "precisionAtK": precision_at_k(baseline_ids, relevant, k),
                "recallAtK": recall_at_k(baseline_ids, relevant, k),
            },
            "routeRules": {
                "ids": ranked_ids[:k],
                "precisionAtK": precision_at_k(ranked_ids, relevant, k),
                "recallAtK": recall_at_k(ranked_ids, relevant, k),
                "unknownRouteCount": len(unknown),
                "verifiedCount": len(verified),
            },
            "hasRouteEvidence": network.valid,
        })
    return {"caseCount": len(output), "k": k, "results": output,
            "notice": "Fixture-only offline comparison. No real-world uplift claim."}


def evaluate_file(input_path: Path, output_path: Path) -> dict:
    cases = json.loads(input_path.read_text(encoding="utf-8"))
    if not isinstance(cases, list):
        raise ValueError("Expected JSON array")
    report = evaluate_cases(cases)
    output_path.parent.mkdir(parents=True, exist_ok=True)
    output_path.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n",
                           encoding="utf-8")
    return report


if __name__ == "__main__":
    import argparse
    parser = argparse.ArgumentParser()
    parser.add_argument("--input", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    print(json.dumps(evaluate_file(args.input, args.output), ensure_ascii=False, indent=2))
