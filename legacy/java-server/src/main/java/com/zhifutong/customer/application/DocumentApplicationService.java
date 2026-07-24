package com.zhifutong.customer.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhifutong.customer.auth.AuthContext;
import com.zhifutong.customer.client.QdrantVectorStore;
import com.zhifutong.customer.config.AppProperties;
import com.zhifutong.customer.domain.DocumentStatus;
import com.zhifutong.customer.domain.DocumentTaskStatus;
import com.zhifutong.customer.entity.DocumentProcessingTask;
import com.zhifutong.customer.entity.KbDocument;
import com.zhifutong.customer.exception.BusinessException;
import com.zhifutong.customer.mapper.DocumentProcessingTaskMapper;
import com.zhifutong.customer.mapper.KbChunkMapper;
import com.zhifutong.customer.mapper.KbDocumentMapper;
import com.zhifutong.customer.service.FileValidator;
import com.zhifutong.customer.vo.DocumentResponse;
import com.zhifutong.customer.vo.PageResult;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.UUID;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

@Service
public class DocumentApplicationService {
    private final KbDocumentMapper documentMapper;
    private final DocumentProcessingTaskMapper taskMapper;
    private final KbChunkMapper chunkMapper;
    private final FileValidator fileValidator;
    private final QdrantVectorStore vectorStore;
    private final AppProperties properties;
    private final TransactionTemplate transactionTemplate;
    private final DocumentProcessingService processingService;

    public DocumentApplicationService(KbDocumentMapper documentMapper, DocumentProcessingTaskMapper taskMapper,
                                      KbChunkMapper chunkMapper, FileValidator fileValidator,
                                      QdrantVectorStore vectorStore, AppProperties properties,
                                      TransactionTemplate transactionTemplate, DocumentProcessingService processingService) {
        this.documentMapper = documentMapper;
        this.taskMapper = taskMapper;
        this.chunkMapper = chunkMapper;
        this.fileValidator = fileValidator;
        this.vectorStore = vectorStore;
        this.properties = properties;
        this.transactionTemplate = transactionTemplate;
        this.processingService = processingService;
    }

    public DocumentResponse upload(MultipartFile file) {
        String extension = fileValidator.validate(file);
        Path storageRoot = Path.of(properties.getDocument().getStoragePath()).toAbsolutePath().normalize();
        try {
            Files.createDirectories(storageRoot);
        } catch (Exception ex) {
            throw new BusinessException("文档存储目录创建失败");
        }

        String storageName = UUID.randomUUID() + "." + extension;
        Path target = storageRoot.resolve(storageName).normalize();
        if (!target.startsWith(storageRoot)) {
            throw new BusinessException("文件存储路径不合法");
        }

        String sha256 = saveAndHash(file, target);
        if (documentMapper.selectCount(new LambdaQueryWrapper<KbDocument>().eq(KbDocument::getFileSha256, sha256)) > 0) {
            deleteQuietly(target);
            throw new BusinessException(HttpStatus.CONFLICT, "相同内容的文档已存在，请勿重复上传");
        }

        try {
            DocumentResponse response = transactionTemplate.execute(status -> {
                LocalDateTime now = LocalDateTime.now();
                KbDocument document = new KbDocument();
                document.setOriginalName(file.getOriginalFilename());
                document.setStorageName(storageName);
                document.setStoragePath(target.toString());
                document.setFileType(extension);
                document.setFileSize(file.getSize());
                document.setFileSha256(sha256);
                document.setUploadedBy(AuthContext.require().userId());
                document.setLockVersion(0);
                document.setStatus(DocumentStatus.PENDING);
                document.setChunkCount(0);
                document.setCreatedAt(now);
                document.setUpdatedAt(now);
                documentMapper.insert(document);

                DocumentProcessingTask task = new DocumentProcessingTask();
                task.setDocumentId(document.getId());
                task.setStatus(DocumentTaskStatus.PENDING);
                task.setRetryCount(0);
                task.setMaxRetryCount(3);
                task.setLockVersion(0);
                task.setCreatedAt(now);
                task.setUpdatedAt(now);
                taskMapper.insert(task);
                return toResponse(document);
            });
            if (response != null) {
                processingService.processAsync(response.id());
            }
            return response;
        } catch (RuntimeException ex) {
            deleteQuietly(target);
            throw ex;
        }
    }

    public DocumentResponse retry(Long id) {
        KbDocument document = require(id);
        if (!document.getStatus().canRetry()) {
            throw new BusinessException("只有失败文档可以重新处理");
        }
        DocumentProcessingTask task = taskMapper.selectOne(new LambdaQueryWrapper<DocumentProcessingTask>()
                .eq(DocumentProcessingTask::getDocumentId, id));
        if (task == null) {
            throw new BusinessException("文档处理任务不存在");
        }
        task.setStatus(DocumentTaskStatus.PENDING);
        task.setRetryCount(0);
        task.setNextRetryAt(null);
        task.setErrorMessage(null);
        task.setUpdatedAt(LocalDateTime.now());
        taskMapper.updateById(task);
        processingService.processAsync(id);
        return toResponse(documentMapper.selectById(id));
    }

    public PageResult<DocumentResponse> list(long page, long size, String keyword) {
        Page<KbDocument> result = documentMapper.selectPage(Page.of(page, size),
                new LambdaQueryWrapper<KbDocument>()
                        .like(keyword != null && !keyword.isBlank(), KbDocument::getOriginalName, keyword)
                        .orderByDesc(KbDocument::getCreatedAt));
        return new PageResult<>(page, size, result.getTotal(), result.getRecords().stream().map(this::toResponse).toList());
    }

    public DocumentResponse get(Long id) {
        return toResponse(require(id));
    }

    public Resource download(Long id) {
        KbDocument document = require(id);
        Path path = Path.of(document.getStoragePath()).normalize();
        if (!Files.exists(path)) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "文档文件不存在");
        }
        return new FileSystemResource(path);
    }

    public String delete(Long id) {
        KbDocument document = require(id);
        try {
            vectorStore.deleteByDocumentId(id);
            chunkMapper.delete(new LambdaQueryWrapper<com.zhifutong.customer.entity.KbChunk>().eq(com.zhifutong.customer.entity.KbChunk::getDocumentId, id));
            taskMapper.delete(new LambdaQueryWrapper<DocumentProcessingTask>().eq(DocumentProcessingTask::getDocumentId, id));
            Files.deleteIfExists(Path.of(document.getStoragePath()));
            documentMapper.deleteById(id);
            return "文档、知识片段、向量索引和本地文件均已删除";
        } catch (Exception ex) {
            throw new BusinessException("文档删除失败: " + ex.getMessage());
        }
    }

    public KbDocument require(Long id) {
        KbDocument document = documentMapper.selectById(id);
        if (document == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "文档不存在");
        }
        return document;
    }

    public DocumentResponse toResponse(KbDocument document) {
        return new DocumentResponse(document.getId(), document.getOriginalName(), document.getFileType(),
                document.getFileSize(), document.getStatus(), document.getChunkCount(), document.getFailureReason(),
                document.getCreatedAt(), document.getUpdatedAt());
    }

    private String saveAndHash(MultipartFile file, Path target) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream input = new DigestInputStream(file.getInputStream(), digest);
                 OutputStream output = Files.newOutputStream(target)) {
                input.transferTo(output);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (Exception ex) {
            deleteQuietly(target);
            throw new BusinessException("文件保存或SHA-256计算失败");
        }
    }

    private void deleteQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (Exception ignored) {
            // Best-effort cleanup.
        }
    }
}
