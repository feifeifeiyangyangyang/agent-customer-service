import pytest

from app.agent.tools.executor import ToolExecutionError, tool_executor
from app.agent.tools.registry import TOOL_REGISTRY, GetOrderDetailArgs, RequestRefundArgs
from app.core.security import AuthenticatedUser


class FakeSession:
    def __init__(self) -> None:
        self.added: list[object] = []

    def add(self, value: object) -> None:
        self.added.append(value)


def test_read_only_order_lookup_policy_is_low_risk() -> None:
    definition = TOOL_REGISTRY["get_order_detail"]

    assert definition.argument_model is GetOrderDetailArgs
    assert definition.policy.read_only is True
    assert definition.policy.side_effect is False
    assert definition.policy.risk_level == "LOW"
    assert definition.policy.allowed_roles == ["CUSTOMER"]


def test_refund_request_policy_requires_side_effect_tracking() -> None:
    definition = TOOL_REGISTRY["request_refund"]

    assert definition.argument_model is RequestRefundArgs
    assert definition.policy.read_only is False
    assert definition.policy.side_effect is True
    assert definition.policy.risk_level == "HIGH"
    assert definition.policy.retry_count == 0


@pytest.mark.asyncio
async def test_tool_executor_validates_arguments_and_records_success() -> None:
    session = FakeSession()
    user = AuthenticatedUser(user_id=1, username="user", name="用户", role="CUSTOMER")

    result = await tool_executor.execute(
        session,  # type: ignore[arg-type]
        "run_1",
        user,
        "get_order_detail",
        {"order_no": "ORD202607140003"},
        lambda args: {"order_no": args.order_no},
        lambda value: f"resolved {value['order_no']}",
    )

    assert result == {"order_no": "ORD202607140003"}
    assert len(session.added) == 1


@pytest.mark.asyncio
async def test_tool_executor_rejects_disallowed_role() -> None:
    session = FakeSession()
    admin = AuthenticatedUser(user_id=2, username="admin", name="管理员", role="ADMIN")

    with pytest.raises(ToolExecutionError):
        await tool_executor.execute(
            session,  # type: ignore[arg-type]
            "run_1",
            admin,
            "get_order_detail",
            {"order_no": "ORD202607140003"},
            lambda args: args,
            lambda _value: "should not run",
        )


@pytest.mark.asyncio
async def test_tool_executor_records_validation_failure() -> None:
    session = FakeSession()
    user = AuthenticatedUser(user_id=1, username="user", name="用户", role="CUSTOMER")

    with pytest.raises(ToolExecutionError):
        await tool_executor.execute(
            session,  # type: ignore[arg-type]
            "run_1",
            user,
            "get_product_information",
            {},
            lambda args: args,
            lambda _value: "should not run",
        )

    assert len(session.added) == 1
