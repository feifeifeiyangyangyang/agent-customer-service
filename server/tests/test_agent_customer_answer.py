from datetime import datetime
from decimal import Decimal

from app.db.models import CustomerOrder, ProductCatalog
from app.schemas.retrieval import RetrievalCandidate
from app.services.agent_service import AgentService


def _signed_pillow_order() -> CustomerOrder:
    product = ProductCatalog(
        id=2,
        product_code="P9",
        product_name="云感靠枕 P9",
        category="居家纺织品",
        sale_status="ON_SALE",
        price=Decimal("129.00"),
        stock_quantity=85,
        dispatch_rule="现货订单通常 24 小时内发货，定制颜色以页面预计时间为准。",
        after_sale_rule="未清洗、未明显使用且包装完整时可提交退货申请。",
    )
    order = CustomerOrder(
        id=13,
        order_no="ORD202607140003",
        user_id=1,
        product_id=2,
        quantity=1,
        amount=Decimal("129.00"),
        status="SIGNED",
        paid_at=datetime(2026, 7, 14, 9, 0),
        expected_ship_at=datetime(2026, 7, 17, 9, 0),
        shipped_at=datetime(2026, 7, 17, 9, 0),
        signed_at=datetime(2026, 7, 18, 9, 0),
        receiver_name="演示用户",
        receiver_phone="13800000000",
        receiver_address="演示地址",
    )
    order.product = product
    return order


def test_after_sale_answer_is_customer_facing_not_raw_kb_dump() -> None:
    service = AgentService()
    answer = service._customer_after_sale_answer(  # noqa: SLF001
        _signed_pillow_order(),
        "我要退货退款",
        "退款处理说明 退款通常按原支付路径退回，不建议客服承诺具体到账分钟数。演示规则中...",
    )

    assert "云感靠枕 P9" in answer
    assert "原支付路径" in answer
    assert "退款处理说明" not in answer
    assert "不建议客服承诺" not in answer
    assert "演示规则" not in answer
    assert "#" not in answer


def test_damage_answer_is_clear_and_actionable() -> None:
    service = AgentService()
    answer = service._customer_after_sale_answer(_signed_pillow_order(), "商品包装破损")  # noqa: SLF001

    assert "外包装" in answer
    assert "照片或视频" in answer
    assert "售后申请" in answer


def test_order_bound_sources_do_not_include_other_product_documents() -> None:
    service = AgentService()
    order = _signed_pillow_order()
    candidates = [
        RetrievalCandidate(
            candidate_id="chunk:1",
            source_type="keyword",
            content="C20 个护耗材拆封后通常不支持无理由退货",
            document_id="1",
            chunk_id="1",
            rule_id=None,
            metadata={"file_name": "商品资料-轻氧洗面巾C20.md"},
            original_score=0.9,
        ),
        RetrievalCandidate(
            candidate_id="chunk:2",
            source_type="keyword",
            content="P9 未清洗、未明显使用且包装完整时可提交退货申请",
            document_id="2",
            chunk_id="2",
            rule_id=None,
            metadata={"file_name": "商品资料-云感靠枕P9.md"},
            original_score=0.8,
        ),
    ]

    sources = service._source_references(candidates, "订单 ORD202607140003 怎么退货", order)  # noqa: SLF001

    assert [source.fileName for source in sources] == ["商品资料-云感靠枕P9.md"]
