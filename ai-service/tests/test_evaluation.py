"""Independent manually annotated synthetic regression—not live route or LLM benchmark."""
import json
from datetime import datetime
from pathlib import Path
from zoneinfo import ZoneInfo

from app.evaluation import evaluate_cases

DATA = Path(__file__).parents[1] / "evaluation" / "synthetic_routes.json"


def test_comparison_enforces_safety_and_is_repeatable():
    data = json.loads(DATA.read_text(encoding="utf-8"))
    now = datetime(2026, 10, 21, 9, 0, tzinfo=ZoneInfo("Asia/Shanghai"))
    one = evaluate_cases(data, now=now)
    two = evaluate_cases(data, now=now)
    assert one == two
    assert one["summary"]["cases_total"] == len(data["cases"]) == 3
    assert one["summary"]["unsafe_route_claims"] == 0
    assert one["source_type"] == "synthetic_offline_fixture"
    assert "not real-user" in one["disclaimer"]
    by = {x["case"]: x for x in one["cases"]}
    assert set(by["connected_walking_graph"]["verified_recommendations"]) == {101, 104}
    assert by["connected_walking_graph"]["route_unverified"] == [103]
    assert by["no_trusted_route_data"]["verified_recommendations"] == []
    assert by["no_trusted_route_data"]["route_unverified"] == [201]
    assert by["tighter_detour_constraint"]["verified_recommendations"] == [104]
    assert 101 not in by["tighter_detour_constraint"]["verified_recommendations"]
