from app.agent.routing import build_rule_based_plan


def test_shipping_question_with_product_keyword_routes_to_order_tool() -> None:
    plan = build_rule_based_plan("我的洗脸巾物流到哪里了")

    assert plan.intent == "SHIPPING_QUERY"
    assert plan.order_reference is not None
    assert plan.order_reference.product_keyword == "洗面巾"
    assert plan.required_tools == ["get_order_detail"]
    assert plan.risk_level == "LOW"
    assert plan.requires_confirmation is False


def test_ordinal_order_reference_is_extracted() -> None:
    plan = build_rule_based_plan("第三个商品物流到哪里了")

    assert plan.intent == "SHIPPING_QUERY"
    assert plan.order_reference is not None
    assert plan.order_reference.ordinal_index == 2


def test_all_orders_question_routes_to_order_list_tool() -> None:
    plan = build_rule_based_plan("查询所有订单")

    assert plan.intent == "ORDER_QUERY"
    assert plan.order_reference is not None
    assert plan.order_reference.list_all is True
    assert plan.required_tools == ["list_my_orders"]


def test_refund_request_requires_human_approval() -> None:
    plan = build_rule_based_plan("我要退款 ORD20260719105534381")

    assert plan.intent == "REFUND_REQUEST"
    assert plan.order_reference is not None
    assert plan.order_reference.order_no == "ORD20260719105534381"
    assert plan.risk_level == "HIGH"
    assert plan.requires_confirmation is True
    assert "request_refund" in plan.required_tools


def test_refund_policy_question_routes_to_knowledge_base() -> None:
    plan = build_rule_based_plan("退款一般如何处理？")

    assert plan.intent == "KNOWLEDGE_QUERY"
    assert plan.required_tools == ["search_knowledge_base"]
    assert plan.requires_confirmation is False


def test_after_sale_question_falls_back_to_knowledge_query() -> None:
    plan = build_rule_based_plan("拆封以后还能退吗？")

    assert plan.intent == "KNOWLEDGE_QUERY"
    assert plan.required_tools == ["search_knowledge_base"]
    assert plan.requires_confirmation is False
