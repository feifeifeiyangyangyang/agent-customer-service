package com.zhifutong.customer.rag;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhifutong.customer.domain.DocumentStatus;
import com.zhifutong.customer.entity.KbChunk;
import com.zhifutong.customer.entity.KbDocument;
import com.zhifutong.customer.mapper.KbChunkMapper;
import com.zhifutong.customer.mapper.KbDocumentMapper;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class KeywordKnowledgeSearch {
    private final KbChunkMapper chunkMapper;
    private final KbDocumentMapper documentMapper;

    public KeywordKnowledgeSearch(KbChunkMapper chunkMapper, KbDocumentMapper documentMapper) {
        this.chunkMapper = chunkMapper;
        this.documentMapper = documentMapper;
    }

    public List<KnowledgeChunk> search(String question, int limit) {
        Set<String> terms = terms(question);
        if (terms.isEmpty()) {
            return List.of();
        }

        List<KbDocument> documents = documentMapper.selectList(new LambdaQueryWrapper<KbDocument>()
                .in(KbDocument::getStatus, DocumentStatus.READY, DocumentStatus.COMPLETED));
        if (documents.isEmpty()) {
            return List.of();
        }
        Map<Long, KbDocument> documentMap = documents.stream().collect(Collectors.toMap(KbDocument::getId, Function.identity()));
        List<KbChunk> chunks = chunkMapper.selectList(new LambdaQueryWrapper<KbChunk>()
                .in(KbChunk::getDocumentId, documentMap.keySet()));

        List<KnowledgeChunk> matches = new ArrayList<>();
        for (KbChunk chunk : chunks) {
            KbDocument document = documentMap.get(chunk.getDocumentId());
            double keywordScore = score(document.getOriginalName(), chunk.getContent(), terms);
            if (keywordScore >= 0.50) {
                matches.add(new KnowledgeChunk(document.getId(), document.getOriginalName(), chunk.getChunkIndex(), chunk.getContent(), keywordScore));
            }
        }
        return matches.stream()
                .sorted(Comparator.comparingDouble(KnowledgeChunk::score).reversed())
                .limit(limit)
                .toList();
    }

    private Set<String> terms(String question) {
        String q = question == null ? "" : question.toLowerCase();
        Set<String> terms = new LinkedHashSet<>();
        addIfMentioned(q, terms, List.of("发货", "物流", "出库", "送达", "预售", "什么时候发", "多久发"));
        addIfMentioned(q, terms, List.of("退款", "退钱", "到账", "原路", "仅退款", "退货退款"));
        addIfMentioned(q, terms, List.of("退货", "换货", "拆封", "二次销售", "无理由", "开箱"));
        addIfMentioned(q, terms, List.of("破损", "损坏", "坏了", "漏发", "补发", "质量问题"));
        addIfMentioned(q, terms, List.of("登录", "账号", "验证码", "手机号", "密码"));
        addIfMentioned(q, terms, List.of("暖风杯", "h100"));
        addIfMentioned(q, terms, List.of("轻氧洗面巾", "c20", "洗面巾"));
        addIfMentioned(q, terms, List.of("云感靠枕", "p9", "靠枕"));
        return terms;
    }

    private void addIfMentioned(String question, Set<String> terms, List<String> group) {
        for (String term : group) {
            if (question.contains(term.toLowerCase())) {
                terms.addAll(group);
                return;
            }
        }
    }

    private double score(String fileName, String content, Set<String> terms) {
        String haystack = ((fileName == null ? "" : fileName) + "\n" + content).toLowerCase();
        int hits = 0;
        for (String term : terms) {
            if (haystack.contains(term.toLowerCase())) {
                hits++;
            }
        }
        if (hits == 0) {
            return 0;
        }
        double keywordScore = 0.38 + hits * 0.08;
        if (fileName != null && terms.stream().anyMatch(term -> fileName.toLowerCase().contains(term.toLowerCase()))) {
            keywordScore += 0.18;
        }
        return Math.min(keywordScore, 0.96);
    }
}
