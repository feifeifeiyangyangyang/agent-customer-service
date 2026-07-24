package com.zhifutong.customer.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.zhifutong.customer.client.EmbeddingClient;
import com.zhifutong.customer.client.QdrantVectorStore;
import com.zhifutong.customer.config.AppProperties;
import com.zhifutong.customer.domain.DocumentStatus;
import com.zhifutong.customer.domain.DocumentTaskStatus;
import com.zhifutong.customer.entity.DocumentProcessingTask;
import com.zhifutong.customer.entity.KbChunk;
import com.zhifutong.customer.entity.KbDocument;
import com.zhifutong.customer.mapper.DocumentProcessingTaskMapper;
import com.zhifutong.customer.mapper.KbChunkMapper;
import com.zhifutong.customer.mapper.KbDocumentMapper;
import com.zhifutong.customer.rag.KnowledgeChunk;
import com.zhifutong.customer.rag.TextChunker;
import com.zhifutong.customer.service.DocumentParser;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DocumentProcessingService {
    private static final Logger log = LoggerFactory.getLogger(DocumentProcessingService.class);
    private final DocumentProcessingTaskMapper taskMapper;
    private final KbDocumentMapper documentMapper;
    private final KbChunkMapper chunkMapper;
    private final DocumentParser documentParser;
    private final TextChunker textChunker;
    private final EmbeddingClient embeddingClient;
    private final QdrantVectorStore vectorStore;
    private final AppProperties properties;

    public DocumentProcessingService(DocumentProcessingTaskMapper taskMapper, KbDocumentMapper documentMapper,
                                     KbChunkMapper chunkMapper, DocumentParser documentParser, TextChunker textChunker,
                                     EmbeddingClient embeddingClient, QdrantVectorStore vectorStore, AppProperties properties) {
        this.taskMapper = taskMapper;
        this.documentMapper = documentMapper;
        this.chunkMapper = chunkMapper;
        this.documentParser = documentParser;
        this.textChunker = textChunker;
        this.embeddingClient = embeddingClient;
        this.vectorStore = vectorStore;
        this.properties = properties;
    }

    @Async("documentTaskExecutor")
    public void processAsync(Long documentId) {
        DocumentProcessingTask task = taskMapper.selectOne(new LambdaQueryWrapper<DocumentProcessingTask>()
                .eq(DocumentProcessingTask::getDocumentId, documentId));
        if (task != null) {
            processTask(task.getId());
        }
    }

    @Scheduled(fixedDelay = 30_000)
    public void recoverPendingTasks() {
        LocalDateTime now = LocalDateTime.now();
        List<DocumentProcessingTask> tasks = taskMapper.selectList(new LambdaQueryWrapper<DocumentProcessingTask>()
                .in(DocumentProcessingTask::getStatus, DocumentTaskStatus.PENDING, DocumentTaskStatus.RETRY_WAIT)
                .and(wrapper -> wrapper.isNull(DocumentProcessingTask::getNextRetryAt)
                        .or()
                        .le(DocumentProcessingTask::getNextRetryAt, now))
                .last("LIMIT 10"));
        for (DocumentProcessingTask task : tasks) {
            processAsync(task.getDocumentId());
        }
    }

    public void processTask(Long taskId) {
        DocumentProcessingTask task = taskMapper.selectById(taskId);
        if (task == null || !claim(task)) {
            return;
        }
        try {
            processClaimedTask(taskId);
        } catch (Exception ex) {
            markFailedOrRetry(taskId, ex);
        }
    }

    private boolean claim(DocumentProcessingTask task) {
        LocalDateTime now = LocalDateTime.now();
        int updated = taskMapper.update(null, new LambdaUpdateWrapper<DocumentProcessingTask>()
                .eq(DocumentProcessingTask::getId, task.getId())
                .in(DocumentProcessingTask::getStatus, DocumentTaskStatus.PENDING, DocumentTaskStatus.RETRY_WAIT)
                .and(wrapper -> wrapper.isNull(DocumentProcessingTask::getNextRetryAt)
                        .or()
                        .le(DocumentProcessingTask::getNextRetryAt, now))
                .set(DocumentProcessingTask::getStatus, DocumentTaskStatus.RUNNING)
                .set(DocumentProcessingTask::getStartedAt, now)
                .set(DocumentProcessingTask::getUpdatedAt, now)
                .set(DocumentProcessingTask::getLockVersion, task.getLockVersion() + 1));
        return updated == 1;
    }

    @Transactional
    public void processClaimedTask(Long taskId) throws Exception {
        DocumentProcessingTask task = taskMapper.selectById(taskId);
        KbDocument document = documentMapper.selectById(task.getDocumentId());
        LocalDateTime now = LocalDateTime.now();
        updateDocument(document, DocumentStatus.PROCESSING, null, 0);

        String text = documentParser.parse(Path.of(document.getStoragePath()), document.getFileType());
        List<String> texts = textChunker.split(text, properties.getRag().getChunkSize(),
                properties.getRag().getChunkOverlap(), properties.getRag().getMinChunkLength());
        if (texts.isEmpty()) {
            throw new IllegalStateException("文档切分后没有有效片段");
        }

        vectorStore.deleteByDocumentId(document.getId());
        chunkMapper.delete(new LambdaQueryWrapper<KbChunk>().eq(KbChunk::getDocumentId, document.getId()));

        List<KnowledgeChunk> chunks = new ArrayList<>();
        List<float[]> vectors = new ArrayList<>();
        for (int i = 0; i < texts.size(); i++) {
            String chunkText = texts.get(i);
            String pointId = UUID.nameUUIDFromBytes("doc:%d:chunk:%d".formatted(document.getId(), i).getBytes(StandardCharsets.UTF_8)).toString();
            KbChunk chunk = new KbChunk();
            chunk.setDocumentId(document.getId());
            chunk.setChunkIndex(i);
            chunk.setContent(chunkText);
            chunk.setContentHash(sha256(chunkText));
            chunk.setCharCount(chunkText.length());
            chunk.setVectorPointId(pointId);
            chunk.setCreatedAt(now);
            chunk.setUpdatedAt(now);
            chunkMapper.insert(chunk);
            chunks.add(new KnowledgeChunk(document.getId(), document.getOriginalName(), i, chunkText, 1.0));
            vectors.add(embeddingClient.embed(chunkText));
        }

        vectorStore.upsert(chunks, vectors);
        updateDocument(document, DocumentStatus.READY, null, chunks.size());
        task.setStatus(DocumentTaskStatus.SUCCESS);
        task.setFinishedAt(LocalDateTime.now());
        task.setErrorMessage(null);
        task.setUpdatedAt(LocalDateTime.now());
        taskMapper.updateById(task);
    }

    @Transactional
    public void markFailedOrRetry(Long taskId, Exception ex) {
        DocumentProcessingTask task = taskMapper.selectById(taskId);
        if (task == null) {
            return;
        }
        KbDocument document = documentMapper.selectById(task.getDocumentId());
        try {
            vectorStore.deleteByDocumentId(task.getDocumentId());
        } catch (Exception cleanupEx) {
            log.warn("Qdrant cleanup failed for document {}", task.getDocumentId());
        }

        int nextRetry = task.getRetryCount() + 1;
        task.setRetryCount(nextRetry);
        task.setErrorMessage(truncate(ex.getMessage()));
        task.setUpdatedAt(LocalDateTime.now());
        if (nextRetry <= task.getMaxRetryCount()) {
            task.setStatus(DocumentTaskStatus.RETRY_WAIT);
            task.setNextRetryAt(LocalDateTime.now().plusMinutes((long) Math.pow(2, nextRetry - 1)));
            updateDocument(document, DocumentStatus.FAILED, task.getErrorMessage(), 0);
        } else {
            task.setStatus(DocumentTaskStatus.FAILED);
            task.setFinishedAt(LocalDateTime.now());
            updateDocument(document, DocumentStatus.FAILED, task.getErrorMessage(), 0);
        }
        taskMapper.updateById(task);
    }

    private void updateDocument(KbDocument document, DocumentStatus status, String reason, int chunkCount) {
        document.setStatus(status);
        document.setFailureReason(reason);
        document.setChunkCount(chunkCount);
        document.setUpdatedAt(LocalDateTime.now());
        documentMapper.updateById(document);
    }

    private String sha256(String text) throws Exception {
        byte[] hash = MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(hash);
    }

    private String truncate(String text) {
        if (text == null) {
            return "未知错误";
        }
        return text.length() <= 1000 ? text : text.substring(0, 1000);
    }
}
