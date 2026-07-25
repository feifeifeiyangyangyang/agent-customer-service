import asyncio
import inspect
import json
import time
from collections.abc import Awaitable, Callable
from typing import Any

from pydantic import BaseModel, ValidationError
from sqlalchemy.ext.asyncio import AsyncSession

from app.agent.tools.registry import TOOL_REGISTRY
from app.core.security import AuthenticatedUser
from app.db.models import AgentToolCall


class ToolExecutionError(RuntimeError):
    pass


ToolHandler = Callable[[Any], Any | Awaitable[Any]]
ResultSummarizer = Callable[[Any], str]


class ToolExecutor:
    async def execute(
        self,
        session: AsyncSession,
        run_id: str,
        user: AuthenticatedUser,
        tool_name: str,
        arguments: dict[str, Any],
        handler: ToolHandler,
        summarize: ResultSummarizer,
    ) -> Any:
        definition = TOOL_REGISTRY.get(tool_name)
        if definition is None:
            raise ToolExecutionError(f"Unknown tool: {tool_name}")
        if user.role not in definition.policy.allowed_roles:
            raise ToolExecutionError(f"Role {user.role} is not allowed to call {tool_name}")
        try:
            parsed_args = definition.argument_model.model_validate(arguments)
        except ValidationError as exc:
            await self._record_call(
                session=session,
                run_id=run_id,
                tool_name=tool_name,
                redacted_arguments=self._redact(arguments, definition.policy.redaction),
                result_summary=f"argument validation failed: {exc.errors()[0]['msg']}",
                success=False,
                retry_count=0,
                duration_ms=0,
            )
            raise ToolExecutionError(f"Invalid arguments for {tool_name}") from exc

        redacted_arguments = self._redact(parsed_args.model_dump(mode="json"), definition.policy.redaction)
        max_attempts = definition.policy.retry_count + 1
        last_error: Exception | None = None
        started = time.perf_counter()
        for attempt in range(max_attempts):
            try:
                result = await asyncio.wait_for(
                    self._call_handler(handler, parsed_args),
                    timeout=definition.policy.timeout_seconds,
                )
                await self._record_call(
                    session=session,
                    run_id=run_id,
                    tool_name=tool_name,
                    redacted_arguments=redacted_arguments,
                    result_summary=summarize(result),
                    success=True,
                    retry_count=attempt,
                    duration_ms=self._duration_ms(started),
                )
                return result
            except Exception as exc:
                last_error = exc
                if attempt + 1 >= max_attempts:
                    break

        await self._record_call(
            session=session,
            run_id=run_id,
            tool_name=tool_name,
            redacted_arguments=redacted_arguments,
            result_summary=f"execution failed: {type(last_error).__name__ if last_error else 'unknown'}",
            success=False,
            retry_count=max_attempts - 1,
            duration_ms=self._duration_ms(started),
        )
        raise ToolExecutionError(f"Tool execution failed: {tool_name}") from last_error

    async def _call_handler(self, handler: ToolHandler, parsed_args: BaseModel) -> Any:
        value = handler(parsed_args)
        if inspect.isawaitable(value):
            return await value
        return value

    async def _record_call(
        self,
        session: AsyncSession,
        run_id: str,
        tool_name: str,
        redacted_arguments: dict[str, Any],
        result_summary: str,
        success: bool,
        retry_count: int,
        duration_ms: int,
    ) -> None:
        session.add(
            AgentToolCall(
                run_id=run_id,
                tool_name=tool_name,
                redacted_arguments_json=json.dumps(redacted_arguments, ensure_ascii=False),
                result_summary=result_summary[:500],
                success=success,
                retry_count=retry_count,
                duration_ms=duration_ms,
            )
        )

    def _redact(self, arguments: dict[str, Any], redaction: str) -> dict[str, Any]:
        redacted = dict(arguments)
        if redaction in {"mask_receiver_fields", "mask_contact"}:
            for key in ["receiver_name", "receiver_phone", "receiver_address", "contact"]:
                if key in redacted and redacted[key]:
                    redacted[key] = "***"
        if redaction == "snippet_only" and "query" in redacted:
            redacted["query"] = str(redacted["query"])[:80]
        return redacted

    def _duration_ms(self, started: float) -> int:
        return max(0, int((time.perf_counter() - started) * 1000))


tool_executor = ToolExecutor()
