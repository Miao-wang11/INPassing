"""Stage 6 reproducible no-hallucination baseline, with synthetic TEST-ONLY data."""
from datetime import datetime, timedelta
from zoneinfo import ZoneInfo

import pytest

from app.evaluation import evaluate_cases, precision_at_k, recall_at_k
from tests.test_route_matching import graph_fixture, task


def fixture_case(with_network=True):
    return {
        "caseId": "synthetic-campus-route-001",
        "campusId": 7,
        "network": graph_fixture() if with_network else {"campusId":7,"places":[],"edges":[]},
        "slots": {
            "origin": "图书馆", "destination": "三号宿舍",
            "departure_at": (datetime.now(ZoneInfo("Asia/Shanghai"))
                             + timedelta(hours=1)).isoformat(),
            "max_detour_minutes": 10,
        },
        "candidates": [
            task(11, pickup="快递站", delivery="教学楼", tip=4, delivery_fee=1),
            task(12, pickup="未知路段", delivery="教学楼", tip=20, delivery_fee=1),
        ],
        "relevantTaskIds": [11],
    }


def test_ranking_vs_fee_sorting_baseline_on_synthetic_case():
    result = evaluate_cases([fixture_case()])
    x = result["results"][0]
    assert x["baseline"]["ids"] == [12, 11]
    assert x["routeRules"]["ids"] == [11]
    assert x["routeRules"]["unknownRouteCount"] == 1
    assert x["baseline"]["precisionAtK"] == pytest.approx(1/3)
    assert x["routeRules"]["precisionAtK"] == pytest.approx(1/3)
    assert result["notice"].startswith("Fixture-only")


def test_missing_network_does_not_invent_ranked_results():
    x = evaluate_cases([fixture_case(False)])["results"][0]
    assert x["routeRules"]["ids"] == []
    assert x["routeRules"]["verifiedCount"] == 0
    assert x["routeRules"]["unknownRouteCount"] == 2
    assert x["routeRules"]["recallAtK"] == 0


def test_relevant_empty_is_not_falsely_reported_perfect_recall():
    assert recall_at_k([], set(), 3) is None
    assert precision_at_k([], set(), 3) == 0
    with pytest.raises(ValueError):
        evaluate_cases([])
    with pytest.raises(ValueError):
        precision_at_k([1], {1}, 0)
