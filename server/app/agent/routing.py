import re

from app.schemas.agent import AgentPlan, OrderReference

ORDER_NO_PATTERN = re.compile(r"(ORD[0-9A-Z]{8,})", re.IGNORECASE)


def build_rule_based_plan(question: str) -> AgentPlan:
    clean = question.strip()
    order_ref = _extract_order_reference(clean)
    if _is_order_list_query(clean):
        return AgentPlan(
            intent="ORDER_QUERY",
            goal=clean,
            order_reference=OrderReference(list_all=True),
            product_reference=None,
            required_tools=["list_my_orders"],
            action_type=None,
            risk_level="LOW",
            requires_confirmation=False,
            missing_information=[],
            decision_reason="用户希望查看自己已下单商品或订单列表，优先查询订单列表工具。",
        )
    if any(word in clean for word in ["取消", "撤销订单"]):
        return AgentPlan(
            intent="CANCEL_ORDER",
            goal=clean,
            order_reference=order_ref or OrderReference(latest="最近" in clean),
            product_reference=None,
            required_tools=["get_order_detail", "request_order_cancellation"],
            action_type="ORDER_CANCELLATION",
            risk_level="HIGH",
            requires_confirmation=True,
            missing_information=[] if order_ref else ["order_reference"],
            decision_reason="用户表达了取消订单意图，属于高风险有副作用操作。",
        )
    if _is_refund_action_request(clean, order_ref):
        return AgentPlan(
            intent="REFUND_REQUEST",
            goal=clean,
            order_reference=order_ref,
            product_reference=_extract_product_keyword(clean),
            required_tools=["get_order_detail", "request_refund"],
            action_type="REFUND",
            risk_level="HIGH",
            requires_confirmation=True,
            missing_information=[] if order_ref or _extract_product_keyword(clean) else ["order_reference"],
            decision_reason="用户表达了退款意图，必须经过确认和管理员审批。",
        )
    if order_ref and order_ref.order_no and _is_after_sale_policy_query(clean):
        return AgentPlan(
            intent="KNOWLEDGE_QUERY",
            goal=clean,
            order_reference=order_ref,
            product_reference=_extract_product_keyword(clean),
            required_tools=["search_knowledge_base"],
            action_type=None,
            risk_level="LOW",
            requires_confirmation=False,
            missing_information=[],
            decision_reason="用户提供了明确订单号并咨询售后规则，结合订单状态检索售后规则。",
        )
    if order_ref and order_ref.order_no:
        return AgentPlan(
            intent="ORDER_QUERY",
            goal=clean,
            order_reference=order_ref,
            product_reference=None,
            required_tools=["get_order_detail"],
            action_type=None,
            risk_level="LOW",
            requires_confirmation=False,
            missing_information=[],
            decision_reason="用户提供了明确订单号，优先查询该订单真实状态。",
        )
    product_ref = _extract_product_keyword(clean)
    if product_ref and _is_product_rule_query(clean):
        return AgentPlan(
            intent="PRODUCT_QUERY",
            goal=clean,
            order_reference=None,
            product_reference=product_ref,
            required_tools=["get_product_information"],
            action_type=None,
            risk_level="LOW",
            requires_confirmation=False,
            missing_information=[],
            decision_reason="用户咨询商品规则或商品基础信息，读取商品资料表。",
        )
    if any(word in clean for word in ["物流", "快递", "发货", "到哪", "到哪里", "什么时候到"]):
        return AgentPlan(
            intent="SHIPPING_QUERY",
            goal=clean,
            order_reference=order_ref,
            product_reference=product_ref,
            required_tools=["get_order_detail"],
            action_type=None,
            risk_level="LOW",
            requires_confirmation=False,
            missing_information=[],
            decision_reason="问题涉及订单物流或发货，优先使用订单工具。",
        )
    if any(word in clean for word in ["商品", "库存", "价格", "杯", "洗脸巾", "洗面巾", "靠枕"]):
        return AgentPlan(
            intent="PRODUCT_QUERY",
            goal=clean,
            order_reference=order_ref,
            product_reference=product_ref,
            required_tools=["get_product_information"],
            action_type=None,
            risk_level="LOW",
            requires_confirmation=False,
            missing_information=[],
            decision_reason="问题涉及商品资料，使用商品信息工具。",
        )
    return AgentPlan(
        intent="KNOWLEDGE_QUERY",
        goal=clean,
        order_reference=order_ref,
        product_reference=_extract_product_keyword(clean),
        required_tools=["search_knowledge_base"],
        action_type=None,
        risk_level="LOW",
        requires_confirmation=False,
        missing_information=[],
        decision_reason="未命中明确业务操作，进入知识库检索。",
    )


def _is_refund_action_request(question: str, order_ref: OrderReference | None) -> bool:
    if not any(word in question for word in ["退款", "退钱"]):
        return False
    policy_words = ["如何", "怎么", "流程", "多久", "几天", "一般", "规则", "说明", "方式"]
    if any(word in question for word in policy_words) and order_ref is None:
        return False
    action_words = ["我要", "帮我", "申请", "办理", "退一下", "给我退", "这单", "订单"]
    return order_ref is not None or any(word in question for word in action_words)


def _is_after_sale_policy_query(question: str) -> bool:
    terms = ["退货", "退款", "退钱", "售后", "破损", "损坏", "包装", "拆封", "换货", "能不能退", "怎么退"]
    return any(term in question for term in terms)


def _is_product_rule_query(question: str) -> bool:
    terms = ["发货规则", "发货时效", "出库规则", "售后规则", "库存", "价格", "多少钱", "还有货", "在售"]
    return any(term in question for term in terms)


def _extract_order_reference(question: str) -> OrderReference | None:
    match = ORDER_NO_PATTERN.search(question)
    if match:
        return OrderReference(order_no=match.group(1).upper())
    ordinal = _extract_ordinal_index(question)
    if ordinal is not None:
        return OrderReference(ordinal_index=ordinal)
    for keyword, index in [("第一个", 0), ("第一单", 0), ("第二个", 1), ("第二单", 1), ("第三个", 2), ("第三单", 2)]:
        if keyword in question:
            return OrderReference(ordinal_index=index)
    if "最近" in question or "刚买" in question or "刚下单" in question:
        return OrderReference(latest=True)
    product = _extract_product_keyword(question)
    if product:
        return OrderReference(product_keyword=product)
    return None


def _is_order_list_query(question: str) -> bool:
    order_words = ["订单", "下单", "买的", "购买", "商品"]
    list_words = ["所有", "全部", "列表", "列出", "查询", "查看", "分别", "哪些", "已经下单"]
    return any(word in question for word in order_words) and any(word in question for word in list_words)


def _extract_ordinal_index(question: str) -> int | None:
    match = re.search(r"第\s*(\d+)\s*(个|单|笔|条|件|号)?", question)
    if match:
        value = int(match.group(1))
        return value - 1 if value > 0 else None
    chinese_digits = {
        "一": 1,
        "二": 2,
        "三": 3,
        "四": 4,
        "五": 5,
        "六": 6,
        "七": 7,
        "八": 8,
        "九": 9,
        "十": 10,
        "十一": 11,
        "十二": 12,
        "十三": 13,
        "十四": 14,
        "十五": 15,
        "十六": 16,
        "十七": 17,
        "十八": 18,
        "十九": 19,
        "二十": 20,
    }
    for word, value in sorted(chinese_digits.items(), key=lambda item: len(item[0]), reverse=True):
        if f"第{word}" in question:
            return value - 1
    return None


def _extract_product_keyword(question: str) -> str | None:
    upper = question.upper()
    if "洗脸巾" in question or "洗面巾" in question or "洁面巾" in question:
        return "洗面巾"
    if "C20" in upper:
        return "C20"
    if "杯" in question or "H100" in upper:
        return "杯"
    if "P9" in upper:
        return "P9"
    if "靠枕" in question or "枕头" in question:
        return "靠枕"
    return None
