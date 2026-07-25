import asyncio
import hashlib
import uuid
from datetime import datetime
from pathlib import Path

from sqlalchemy import delete, select

from app.core.config import settings
from app.db.models import KbChunk, KbDocument
from app.db.session import dispose_engine, session_factory
from app.embeddings.mock_embedding import MockEmbeddingClient
from app.rag.chunker import chunk_text
from app.repositories.qdrant_store import VectorChunkPayload, qdrant_store

# ruff: noqa: E501

DEMO_DOCUMENTS = {
    "账号问题处理指南.md": """# 账号问题处理指南
适用范围：用户无法登录、忘记密码、手机号变更、账号冻结、收不到验证码、账号信息不一致等问题。

登录失败：先确认账号是否输入正确，再检查是否使用了演示账号 user / 123456 或管理员账号 admin / admin123。若提示密码错误，不能在聊天中展示或重置真实密码，应引导用户走身份验证流程。

验证码问题：如果用户收不到验证码，先确认手机号是否仍在使用、短信是否被拦截、网络是否正常。连续多次发送失败时建议等待 5 分钟后重试；仍失败则转人工核实。

账号冻结：如果账号因多次登录失败或异常操作被冻结，不应让模型直接解冻。客服可创建工单，由管理员核实用户身份和风险记录后处理。

信息修改：收货手机号、地址、昵称等非敏感信息可以引导用户在个人中心修改；登录手机号、实名信息、支付账户等敏感信息必须转人工审核。

转人工条件：涉及身份认证、资金安全、实名信息、账号冻结、疑似盗号、注销账号、隐私数据导出或删除时，必须转人工。""",
    "退款处理说明.md": """# 退款处理说明
退款通常按原支付路径退回，不建议客服承诺具体到账分钟数。演示规则中，未发货订单在审核通过后进入退款处理中；已发货或已签收订单，需要先完成退货、拒收或售后审核，再处理退款。

未发货订单：用户提交退款后，系统生成退款申请，管理员审核通过后订单进入退款处理中。若订单已经进入仓库拣货或打包阶段，可能需要先拦截发货。

已发货订单：需要结合物流状态判断。运输中订单一般不能直接退款，需等待拒收、退回或人工确认。已签收订单需要用户先说明退货原因、商品状态和包装情况。

到账路径：退款原则上原路退回。银行卡、支付平台、活动券、余额等可能有不同到账时效；优惠券是否退回取决于活动规则。

常见问答：用户问“退款多久到账”时，可回答“审核通过后会进入退款处理中，具体到账以支付渠道为准”。用户问“能不能马上退”时，需要先查订单状态。

转人工条件：金额异常、支付路径变更、部分退款、组合优惠、订单争议、超过售后期或用户要求加急到账时，转人工处理。""",
    "退换货政策.md": """# 退换货政策
本文件用于演示企业知识库问答，不代表任何真实企业政策。

通用退货条件：商品未明显使用、配件齐全、包装没有严重损坏，且不影响二次销售时，可以提交退货申请。若商品已经明显使用、配件缺失、包装严重破损，需要转人工判断。

拆封说明：拆封不等于一定不能退。普通商品拆封后仍需看是否影响二次销售；个护耗材、贴身用品、一次性耗材等商品，拆封后通常不支持无理由退货，质量问题除外。

换货条件：商品存在质量问题、破损、漏发、错发、无法正常使用时，可以申请换货或补发。用户需要提供照片、视频、快递面单或问题描述。

退货运费：非质量问题或用户主观原因退货，退回运费通常由用户承担；质量问题、破损、错发、漏发经审核属实，运费由商家承担或补贴。

售后期：演示规则按签收后 7 天内优先处理。超过售后期、订单状态异常或凭证不足时，建议转人工复核。""",
    "商品资料-暖风杯H100.md": """# 商品资料：暖风杯 H100
商品编码：H100
分类：小家电
价格：199.00 元
库存：40 件
状态：在售

商品定位：暖风杯 H100 是演示用小家电商品，适合作为桌面保温、暖饮场景的客服问答样例。页面资料强调便携、加热稳定和冬季办公使用。

发货规则：现货订单通常在付款后 48 小时内发货，预售或活动高峰可能顺延。用户咨询“什么时候发货”时，应优先查询订单预计发货时间，再补充该商品发货规则。

售后规则：未影响二次销售可申请退货；质量问题可提供照片或视频凭证申请换货。若涉及通电异常、加热异常、外壳破损、漏液等问题，需要转人工或售后审核。

推荐客服回答：先确认用户订单号或最近订单，再说明库存、价格、发货规则和售后条件。不能承诺真实品牌质保。""",
    "商品资料-轻氧洗面巾C20.md": """# 商品资料：轻氧洗面巾 C20
商品编码：C20
分类：个护耗材
价格：39.90 元
库存：318 件
状态：在售

商品定位：轻氧洗面巾 C20 是一次性个护耗材类演示商品，适合回答库存、发货、拆封退货、质量问题等问题。

发货规则：工作日 18 点前付款通常当天出库，偏远地区以物流时效为准。用户问发货时，应结合订单支付时间、预计发货时间和物流记录回答。

售后规则：个护耗材拆封后通常不支持无理由退货；质量问题可转人工核实。如果用户反馈污染、破损、数量不符、包装严重损坏，应要求保留包装和照片凭证。

退货提醒：不能把普通商品“拆封仍可能退货”的规则直接套用到 C20。若用户已拆封且没有质量问题，应说明通常不支持无理由退货，并建议人工复核具体订单。""",
    "商品资料-云感靠枕P9.md": """# 商品资料：云感靠枕 P9
商品编码：P9
分类：居家纺织品
价格：129.00 元
库存：85 件
状态：在售

商品定位：云感靠枕 P9 是居家纺织品演示商品，适合回答靠枕库存、物流、签收后退货、包装破损、清洗后是否可退等问题。

发货规则：现货订单通常 24 小时内发货，定制颜色以页面预计时间为准。若订单已签收，则不应继续回答“还未发货”，应说明已签收并引导售后问题。

售后规则：未清洗、未明显使用且包装完整时可提交退货申请。若已经清洗、明显使用、沾污、变形、缺少吊牌或包装严重破损，需要转人工判断。

破损处理：签收后发现靠枕破损、压痕严重、污渍明显，应保留外包装、快递面单和照片或视频凭证，可进入售后审核。""",
    "商品损坏售后流程.md": """# 商品损坏售后流程
适用范围：用户收到商品后发现破损、裂痕、漏液、污染、无法正常使用、数量不符或错发漏发。

第一步：确认订单状态。未签收时优先关注物流异常或拒收；已签收时进入售后判断。具体订单状态优先于通用文档。

第二步：收集凭证。建议用户保留商品、外包装、快递面单，并拍摄清晰照片或视频。照片应包含损坏位置、整体包装、订单或面单信息。

第三步：区分责任。运输破损、质量问题、错发漏发经审核属实，可换货、补发、退款或转人工处理。用户主观原因或凭证不足，需要人工复核。

第四步：处理方式。轻微包装压痕但商品可正常使用，可解释并转人工补偿判断；影响使用或明显破损，可引导提交售后申请。

禁止事项：客服不能凭空承诺必赔、必退、立即退款；不能要求用户丢弃商品和包装后再申请。""",
    "发货与物流规则.md": """# 发货与物流规则
订单物流回答必须优先查询订单真实状态，其次参考商品发货规则，最后才使用通用物流说明。

状态解释：待发货表示仓库尚未交运；已发货表示已经生成物流或交给承运商；运输中表示物流已有节点；已签收表示配送完成；已取消订单不再进入发货流程。

商品差异：暖风杯 H100 现货通常付款后 48 小时内发货；轻氧洗面巾 C20 工作日 18 点前付款通常当天出库；云感靠枕 P9 现货通常 24 小时内发货，定制颜色以页面预计时间为准。

物流无更新：如果超过预计发货时间仍无物流，建议转人工催仓；如果已发货但 24 小时无节点，建议等待承运商同步或转人工查询。

用户问法处理：问“第几个订单物流到哪里”时按订单列表序号查询；问具体商品名但有多笔同款订单时，不要默认选择，应列出订单让用户确认。""",
    "demo-after-sale-policy.md": """# 售后与退换货政策
本文件用于演示企业知识库问答，不代表任何真实企业政策。

破损商品处理：用户收到商品后发现破损、裂痕、漏液、无法正常使用，建议先保留商品、外包装、快递面单，并拍摄清晰照片或视频。客服可先引导用户提交售后申请；如破损情况明显，可以进入换货或补发流程。若缺少凭证，需要转人工进一步核实。

拆封后退货：商品拆封不等于一定不能退。普通商品若未明显使用、配件齐全、包装没有严重损坏，且不影响二次销售，可以提交退货申请。个护耗材、贴身用品和一次性耗材拆封后通常不支持无理由退货，质量问题除外。

退款处理：退款通常按原支付路径退回。未发货订单审核通过后，一般进入退款处理中；已发货或已签收订单，需要先完成退货或售后审核，再处理退款。

退货运费：非质量问题、用户主观原因退货时，退回运费通常由用户承担；若商品存在质量问题、破损、错发或漏发，经审核属实后，退回运费由商家承担或补贴。

质量问题换货：商品存在演示规则中的质量问题时，可以申请换货。用户需要提供问题描述、照片或视频凭证，客服确认后进入售后审核。""",
}


async def sync_document(filename: str, content: str) -> None:
    factory = session_factory()
    async with factory() as session:
        now = datetime.now()
        encoded = content.encode("utf-8")
        digest = hashlib.sha256(encoded).hexdigest()
        storage_root = Path(settings.document_storage_path)
        storage_root.mkdir(parents=True, exist_ok=True)
        storage_path = storage_root / filename
        storage_path.write_bytes(encoded)

        document = (
            await session.execute(select(KbDocument).where(KbDocument.original_name == filename))
        ).scalar_one_or_none()
        if document is None:
            document = KbDocument(
                original_name=filename,
                storage_name=filename,
                storage_path=str(storage_path),
                file_type="md",
                file_size=len(encoded),
                file_sha256=digest,
                uploaded_by=None,
                status="READY",
                chunk_count=0,
                created_at=now,
                updated_at=now,
            )
            session.add(document)
            await session.flush()
        else:
            document.storage_name = filename
            document.storage_path = str(storage_path)
            document.file_type = "md"
            document.file_size = len(encoded)
            document.file_sha256 = digest
            document.status = "READY"
            document.failure_reason = None
            document.updated_at = now

        await session.execute(delete(KbChunk).where(KbChunk.document_id == document.id))
        chunks = chunk_text(content, max_chars=900, overlap=120)
        embedding = MockEmbeddingClient(settings.embedding_dimension)
        vector_rows = []
        for chunk in chunks:
            point_id = str(uuid.uuid5(uuid.NAMESPACE_URL, f"kb:{document.id}:{chunk.index}:{chunk.content_hash}"))
            row = KbChunk(
                document_id=document.id,
                chunk_index=chunk.index,
                content=chunk.content,
                content_hash=chunk.content_hash,
                char_count=chunk.char_count,
                vector_point_id=point_id,
                created_at=now,
                updated_at=now,
            )
            session.add(row)
            vector_rows.append((row, point_id))
        await session.flush()
        await qdrant_store.delete_document(document.id)
        await qdrant_store.upsert_chunks(
            [
                (
                    point_id,
                    embedding.embed(row.content),
                    VectorChunkPayload(
                        document_id=document.id,
                        chunk_id=row.id,
                        file_name=document.original_name,
                        content=row.content,
                    ),
                )
                for row, point_id in vector_rows
            ]
        )
        document.chunk_count = len(vector_rows)
        await session.commit()


async def main() -> None:
    for filename, content in DEMO_DOCUMENTS.items():
        await sync_document(filename, content)
    print(f"synced_documents={len(DEMO_DOCUMENTS)}")


async def run() -> None:
    try:
        await main()
    finally:
        await dispose_engine()


if __name__ == "__main__":
    asyncio.run(run())
