import argparse
import json
import time
from collections import defaultdict
from dataclasses import dataclass
from pathlib import Path
from typing import Any

DATASET_VERSION = "after_sale_v1"
EVAL_CONFIG_VERSION = "workflow-eval-v1"


@dataclass(frozen=True)
class EvalCase:
    case_id: str
    category: str
    conversation_history: list[str]
    question: str
    expected_intent: str
    expected_tools: list[str]
    expected_risk_level: str
    expected_requires_confirmation: bool
    expected_source_types: list[str]
    expected_answer_facts: list[str]
    forbidden_answer_facts: list[str]


def dataset_path() -> Path:
    return Path(__file__).resolve().parent / "datasets" / "after_sale_v1.jsonl"


def load_cases(path: Path | None = None) -> list[EvalCase]:
    target = path or dataset_path()
    cases: list[EvalCase] = []
    for line_no, line in enumerate(target.read_text(encoding="utf-8").splitlines(), start=1):
        if not line.strip():
            continue
        raw = json.loads(line)
        try:
            cases.append(EvalCase(**raw))
        except TypeError as exc:
            raise ValueError(f"Invalid eval case at {target}:{line_no}: {exc}") from exc
    return cases


def base_parser(description: str) -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description=description)
    parser.add_argument("--dataset", type=Path, default=dataset_path())
    parser.add_argument("--output", type=Path, default=None)
    parser.add_argument("--pretty", action="store_true")
    return parser


def write_report(report: dict[str, Any], output: Path | None, pretty: bool) -> None:
    payload = json.dumps(report, ensure_ascii=False, indent=2 if pretty else None)
    if output is not None:
        output.parent.mkdir(parents=True, exist_ok=True)
        output.write_text(payload + "\n", encoding="utf-8")
    print(payload)


def summarize_failures(failures: list[dict[str, Any]]) -> dict[str, int]:
    counts: dict[str, int] = defaultdict(int)
    for failure in failures:
        counts[str(failure["category"])] += 1
    return dict(sorted(counts.items()))


def now_ms() -> int:
    return int(time.perf_counter() * 1000)


def ratio(numerator: int, denominator: int) -> float | None:
    if denominator == 0:
        return None
    return round(numerator / denominator, 4)
