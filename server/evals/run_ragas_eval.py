from __future__ import annotations

import argparse
import importlib
import json
import os
from pathlib import Path
from typing import Any, cast


def _load_payload(path: Path) -> dict[str, Any]:
    return cast(dict[str, Any], json.loads(path.read_text(encoding="utf-8")))


def _normalise_base_url(value: str) -> str:
    return value.rstrip("/")


def _build_dataset(samples: list[dict[str, Any]]) -> Any:
    try:
        datasets_module = importlib.import_module("datasets")
    except ImportError as exc:  # pragma: no cover - exercised in isolated eval env
        raise RuntimeError("datasets is required. Install evals/requirements-ragas.txt in a separate venv.") from exc

    return datasets_module.Dataset.from_dict(
        {
            "question": [sample["question"] for sample in samples],
            "answer": [sample["answer"] for sample in samples],
            "contexts": [sample["contexts"] for sample in samples],
            "ground_truth": [sample["ground_truth"] for sample in samples],
        }
    )


def _load_ragas_metrics() -> list[Any]:
    try:
        metrics_module = importlib.import_module("ragas.metrics")
    except ImportError as exc:  # pragma: no cover - exercised in isolated eval env
        raise RuntimeError("ragas is required. Install evals/requirements-ragas.txt in a separate venv.") from exc
    return [
        metrics_module.faithfulness,
        metrics_module.answer_relevancy,
        metrics_module.context_precision,
        metrics_module.context_recall,
    ]


def _patch_ragas_temperature() -> None:
    """Zhipu-compatible patch: RAGAS 0.2.x sends 1e-8 for deterministic calls."""
    try:
        llm_base_module = importlib.import_module("ragas.llms.base")
    except ImportError:
        return

    def zhipu_safe_temperature(self: Any, n: int) -> float:
        return 0.30 if n > 1 else 0.00

    if hasattr(llm_base_module, "BaseRagasLLM"):
        llm_base_module.BaseRagasLLM.get_temperature = zhipu_safe_temperature


def _build_judge_llm() -> Any:
    try:
        openai_module = importlib.import_module("langchain_openai")
    except ImportError as exc:  # pragma: no cover - exercised in isolated eval env
        raise RuntimeError("langchain-openai is required for RAGAS judge model.") from exc

    api_key = os.getenv("RAGAS_LLM_API_KEY") or os.getenv("LLM_API_KEY")
    if not api_key:
        raise RuntimeError("RAGAS_LLM_API_KEY or LLM_API_KEY is required for RAGAS judge metrics.")
    base_url = os.getenv("RAGAS_LLM_BASE_URL") or os.getenv("LLM_BASE_URL") or ""
    return openai_module.ChatOpenAI(
        api_key=api_key,
        base_url=_normalise_base_url(base_url),
        model=os.getenv("RAGAS_LLM_MODEL_NAME") or os.getenv("LLM_MODEL_NAME", "glm-4-flash"),
        temperature=float(os.getenv("RAGAS_LLM_TEMPERATURE", "0")),
        timeout=60,
    )


def _build_judge_embeddings() -> Any:
    try:
        openai_module = importlib.import_module("langchain_openai")
    except ImportError as exc:  # pragma: no cover - exercised in isolated eval env
        raise RuntimeError("langchain-openai is required for RAGAS embedding-backed metrics.") from exc

    api_key = os.getenv("RAGAS_EMBEDDING_API_KEY") or os.getenv("EMBEDDING_API_KEY")
    if not api_key:
        raise RuntimeError("RAGAS_EMBEDDING_API_KEY or EMBEDDING_API_KEY is required for RAGAS metrics.")
    model_name = os.getenv("RAGAS_EMBEDDING_MODEL_NAME") or os.getenv("EMBEDDING_MODEL_NAME", "embedding-3")
    dimension_value = os.getenv("RAGAS_EMBEDDING_DIMENSION") or os.getenv("EMBEDDING_DIMENSION") or "1024"
    dimensions = int(dimension_value)
    base_url = os.getenv("RAGAS_EMBEDDING_BASE_URL") or os.getenv("EMBEDDING_BASE_URL") or ""
    supports_dimensions = model_name in {"embedding-3", "text-embedding-3-small", "text-embedding-3-large"}
    return openai_module.OpenAIEmbeddings(
        api_key=api_key,
        base_url=_normalise_base_url(base_url),
        model=model_name,
        dimensions=dimensions if supports_dimensions else None,
        timeout=60,
    )


def _serialise_result(result: Any) -> dict[str, Any]:
    if hasattr(result, "to_pandas"):
        frame = result.to_pandas()
        rows = json.loads(frame.to_json(orient="records", force_ascii=False))
        metric_names = [
            column
            for column in frame.columns
            if column not in {"question", "answer", "contexts", "ground_truth", "ground_truths"}
        ]
        summary = {}
        for name in metric_names:
            values = [row.get(name) for row in rows if isinstance(row.get(name), int | float)]
            summary[name] = round(sum(values) / len(values), 4) if values else None
        return {"metrics": summary, "rows": rows}
    if isinstance(result, dict):
        return {"metrics": dict(result), "rows": []}
    return {"metrics": {}, "rows": [], "raw": str(result)}


def main() -> None:
    parser = argparse.ArgumentParser(description="Run RAGAS quality metrics from a project-side RAGAS dataset file.")
    parser.add_argument("--input", type=Path, default=Path("evals/reports/ragas_dataset_latest.json"))
    parser.add_argument("--output", type=Path, default=Path("evals/reports/ragas_eval_latest.json"))
    parser.add_argument("--limit", type=int, default=8, help="Limit judge calls for local smoke evaluation.")
    parser.add_argument("--pretty", action="store_true")
    args = parser.parse_args()

    payload = _load_payload(args.input)
    samples = payload.get("samples", [])
    if args.limit > 0:
        samples = samples[: args.limit]

    try:
        ragas_module = importlib.import_module("ragas")
        _patch_ragas_temperature()

        dataset = _build_dataset(samples)
        result = ragas_module.evaluate(
            dataset,
            metrics=_load_ragas_metrics(),
            llm=_build_judge_llm(),
            embeddings=_build_judge_embeddings(),
        )
        serialised = _serialise_result(result)
        status = "completed"
        error = None
    except Exception as exc:
        serialised = {"metrics": {}, "rows": []}
        status = "failed"
        error = {"type": type(exc).__name__, "message": str(exc)}

    report = {
        "status": status,
        "source_dataset": str(args.input),
        "source_dataset_version": payload.get("dataset_version"),
        "eval_config_version": payload.get("eval_config_version"),
        "ragas_case_count": len(samples),
        "judge_model": os.getenv("RAGAS_LLM_MODEL_NAME") or os.getenv("LLM_MODEL_NAME", "glm-4-flash"),
        "embedding_model": os.getenv("RAGAS_EMBEDDING_MODEL_NAME") or os.getenv("EMBEDDING_MODEL_NAME", "embedding-3"),
        "metrics": serialised["metrics"],
        "rows": serialised["rows"],
        "error": error,
        "notes": [
            "RAGAS uses an LLM-as-judge; these scores are evaluation signals, not deterministic correctness proofs.",
            "Run this script in an isolated RAGAS eval venv, not the main backend .venv.",
            "Recall@K and MRR remain covered by evals.run_retrieval_ablation.",
        ],
    }
    args.output.parent.mkdir(parents=True, exist_ok=True)
    payload_text = json.dumps(report, ensure_ascii=False, indent=2 if args.pretty else None)
    args.output.write_text(payload_text + "\n", encoding="utf-8")
    print(json.dumps(report, ensure_ascii=False, indent=2 if args.pretty else None))


if __name__ == "__main__":
    main()
