from app.agent.tools.registry import TOOL_REGISTRY, GetOrderDetailArgs, RequestRefundArgs


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
