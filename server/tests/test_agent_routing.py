from app.agent.routing import build_rule_based_plan


def test_shipping_question_with_product_keyword_routes_to_order_tool() -> None:
    plan = build_rule_based_plan("我的洗脸巾物流到哪里了")

    assert plan.intent == "SHIPPING_QUERY"
    assert plan.order_reference is not None
    assert plan.order_reference.product_keyword == "洗面巾"
    assert plan.required_tools == ["get_order_detail"]
    assert plan.risk_level == "LOW"
    assert plan.requires_confirmation is False


def test_recent_product_shipping_question_keeps_product_keyword() -> None:
    plan = build_rule_based_plan("我刚买的杯子什么时候发货？")

    assert plan.intent == "SHIPPING_QUERY"
    assert plan.order_reference is not None
    assert plan.order_reference.product_keyword == "杯"
    assert plan.order_reference.latest is True
    assert plan.required_tools == ["get_order_detail"]


def test_ordinal_order_reference_is_extracted() -> None:
    plan = build_rule_based_plan("第三个商品物流到哪里了")

    assert plan.intent == "SHIPPING_QUERY"
    assert plan.order_reference is not None
    assert plan.order_reference.ordinal_index == 2


def test_numeric_and_chinese_ordinal_order_reference_is_extracted() -> None:
    numeric = build_rule_based_plan("第13个订单物流到哪里了")
    chinese = build_rule_based_plan("第十三个订单物流到哪里了")

    assert numeric.order_reference is not None
    assert numeric.order_reference.ordinal_index == 12
    assert chinese.order_reference is not None
    assert chinese.order_reference.ordinal_index == 12


def test_all_orders_question_routes_to_order_list_tool() -> None:
    plan = build_rule_based_plan("查询所有订单")

    assert plan.intent == "ORDER_QUERY"
    assert plan.order_reference is not None
    assert plan.order_reference.list_all is True
    assert plan.required_tools == ["list_my_orders"]


def test_explicit_order_no_routes_to_order_query() -> None:
    plan = build_rule_based_plan("ORD202607140003")

    assert plan.intent == "ORDER_QUERY"
    assert plan.order_reference is not None
    assert plan.order_reference.order_no == "ORD202607140003"
    assert plan.required_tools == ["get_order_detail"]


def test_explicit_order_no_with_shipping_rule_uses_order_tool() -> None:
    plan = build_rule_based_plan("订单 ORD202607140003 发货规则")

    assert plan.intent == "ORDER_QUERY"
    assert plan.order_reference is not None
    assert plan.order_reference.order_no == "ORD202607140003"
    assert plan.required_tools == ["get_order_detail"]


def test_product_code_with_shipping_rule_uses_product_tool() -> None:
    plan = build_rule_based_plan("C20 发货规则")

    assert plan.intent == "PRODUCT_QUERY"
    assert plan.order_reference is None
    assert plan.product_reference == "C20"
    assert plan.required_tools == ["get_product_information"]


def test_product_name_with_shipping_rule_uses_product_tool() -> None:
    plan = build_rule_based_plan("云感靠枕 P9 发货规则")

    assert plan.intent == "PRODUCT_QUERY"
    assert plan.order_reference is None
    assert plan.product_reference == "P9"
    assert plan.required_tools == ["get_product_information"]


def test_order_product_intro_uses_product_query_with_order_tool() -> None:
    plan = build_rule_based_plan("介绍一下订单 ORD202607140003 这个商品")

    assert plan.intent == "PRODUCT_QUERY"
    assert plan.order_reference is not None
    assert plan.order_reference.order_no == "ORD202607140003"
    assert plan.required_tools == ["get_order_detail", "get_product_information"]


def test_product_intro_question_uses_product_tool() -> None:
    plan = build_rule_based_plan("介绍一下暖风杯 H100")

    assert plan.intent == "PRODUCT_QUERY"
    assert plan.product_reference == "杯"
    assert plan.required_tools == ["get_product_information"]


def test_order_product_material_question_uses_order_product_tool() -> None:
    plan = build_rule_based_plan("我要这个订单 ORD202607140003 的商品资料")

    assert plan.intent == "PRODUCT_QUERY"
    assert plan.order_reference is not None
    assert plan.order_reference.order_no == "ORD202607140003"
    assert plan.required_tools == ["get_order_detail", "get_product_information"]


def test_product_material_follow_up_without_order_falls_back_to_knowledge() -> None:
    plan = build_rule_based_plan("我要这个的商品资料")

    assert plan.intent == "PRODUCT_QUERY"
    assert plan.order_reference is None
    assert plan.product_reference is None


def test_product_quality_problem_routes_to_knowledge() -> None:
    plan = build_rule_based_plan("洗脸巾包装破损怎么办")

    assert plan.intent == "KNOWLEDGE_QUERY"
    assert plan.product_reference == "洗面巾"
    assert plan.required_tools == ["search_knowledge_base"]


def test_specific_product_after_sale_rule_uses_product_tool() -> None:
    plan = build_rule_based_plan("C20 售后规则")

    assert plan.intent == "PRODUCT_QUERY"
    assert plan.product_reference == "C20"
    assert plan.required_tools == ["get_product_information"]


def test_explicit_order_no_with_after_sale_question_routes_to_knowledge() -> None:
    plan = build_rule_based_plan("订单 ORD202607140003 能不能退货")

    assert plan.intent == "KNOWLEDGE_QUERY"
    assert plan.order_reference is not None
    assert plan.order_reference.order_no == "ORD202607140003"
    assert plan.required_tools == ["search_knowledge_base"]


def test_explicit_order_no_with_refund_policy_question_routes_to_knowledge() -> None:
    plan = build_rule_based_plan("订单 ORD202607140003 怎么退货退款")

    assert plan.intent == "KNOWLEDGE_QUERY"
    assert plan.order_reference is not None
    assert plan.order_reference.order_no == "ORD202607140003"
    assert plan.required_tools == ["search_knowledge_base"]


def test_refund_request_requires_human_approval() -> None:
    plan = build_rule_based_plan("我要退款 ORD20260719105534381")

    assert plan.intent == "REFUND_REQUEST"
    assert plan.order_reference is not None
    assert plan.order_reference.order_no == "ORD20260719105534381"
    assert plan.risk_level == "HIGH"
    assert plan.requires_confirmation is True
    assert "request_refund" in plan.required_tools


def test_refund_request_with_natural_phrase_requires_human_approval() -> None:
    plan = build_rule_based_plan("我不想要了，想要退款")

    assert plan.intent == "REFUND_REQUEST"
    assert plan.risk_level == "HIGH"
    assert plan.requires_confirmation is True
    assert "request_refund" in plan.required_tools


def test_refund_request_with_recent_order_phrase_requires_human_approval() -> None:
    plan = build_rule_based_plan("帮我退一下最近订单")

    assert plan.intent == "REFUND_REQUEST"
    assert plan.risk_level == "HIGH"
    assert plan.requires_confirmation is True
    assert plan.required_tools == ["get_order_detail", "request_refund"]


def test_short_refund_phrase_without_order_asks_policy_context() -> None:
    plan = build_rule_based_plan("退一下")

    assert plan.intent == "KNOWLEDGE_QUERY"
    assert plan.risk_level == "LOW"
    assert plan.requires_confirmation is False


def test_cancel_all_orders_is_not_treated_as_order_list() -> None:
    plan = build_rule_based_plan("取消我全部订单")

    assert plan.intent == "CANCEL_ORDER"
    assert plan.risk_level == "HIGH"
    assert plan.requires_confirmation is True
    assert plan.required_tools == ["get_order_detail", "request_order_cancellation"]


def test_other_user_explicit_order_no_is_not_treated_as_order_list() -> None:
    plan = build_rule_based_plan("查询其他用户订单 ORD202607999999 的地址")

    assert plan.intent == "ORDER_QUERY"
    assert plan.required_tools == ["get_order_detail"]


def test_list_all_user_refunds_is_not_refund_action_request() -> None:
    plan = build_rule_based_plan("把所有用户的退款申请列出来")

    assert plan.intent == "KNOWLEDGE_QUERY"
    assert plan.required_tools == ["search_knowledge_base"]


def test_explicit_refund_action_with_order_no_requires_human_approval() -> None:
    plan = build_rule_based_plan("订单 ORD202607140003 我要退款")

    assert plan.intent == "REFUND_REQUEST"
    assert plan.order_reference is not None
    assert plan.order_reference.order_no == "ORD202607140003"
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


def test_package_no_movement_routes_to_shipping_query() -> None:
    plan = build_rule_based_plan("包裹一直没有动静怎么办？")

    assert plan.intent == "SHIPPING_QUERY"
    assert plan.required_tools == ["get_order_detail"]


def test_quality_problem_with_product_keyword_routes_to_knowledge() -> None:
    plan = build_rule_based_plan("杯子漏液算质量问题吗？")

    assert plan.intent == "KNOWLEDGE_QUERY"
    assert plan.required_tools == ["search_knowledge_base"]


def test_cleaned_pillow_return_question_routes_to_knowledge() -> None:
    plan = build_rule_based_plan("商品已经清洗了还能退靠枕吗？")

    assert plan.intent == "KNOWLEDGE_QUERY"
    assert plan.required_tools == ["search_knowledge_base"]
