package com.zhifutong.customer.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhifutong.customer.auth.AuthenticatedUser;
import com.zhifutong.customer.domain.TicketCategory;
import com.zhifutong.customer.domain.TicketStatus;
import com.zhifutong.customer.entity.SupportTicket;
import com.zhifutong.customer.entity.TicketOperationLog;
import com.zhifutong.customer.exception.BusinessException;
import com.zhifutong.customer.mapper.SupportTicketMapper;
import com.zhifutong.customer.mapper.TicketOperationLogMapper;
import com.zhifutong.customer.vo.PageResult;
import com.zhifutong.customer.vo.TicketResponse;
import java.time.LocalDateTime;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TicketApplicationService {
    private final SupportTicketMapper ticketMapper;
    private final TicketOperationLogMapper operationLogMapper;
    private final ConversationService conversationService;

    public TicketApplicationService(SupportTicketMapper ticketMapper, TicketOperationLogMapper operationLogMapper,
                                    ConversationService conversationService) {
        this.ticketMapper = ticketMapper;
        this.operationLogMapper = operationLogMapper;
        this.conversationService = conversationService;
    }

    public TicketResponse create(AuthenticatedUser user, Long conversationId, String description, TicketCategory category, String contact) {
        conversationService.requireAccessible(conversationId, user);
        LocalDateTime now = LocalDateTime.now();
        SupportTicket ticket = new SupportTicket();
        ticket.setUserId(user.userId());
        ticket.setTicketNo("T" + UUID.randomUUID().toString().replace("-", "").substring(0, 16));
        ticket.setConversationId(conversationId);
        ticket.setDescription(description);
        ticket.setCategory(category);
        ticket.setContact(contact);
        ticket.setStatus(TicketStatus.OPEN);
        ticket.setPriority("NORMAL");
        ticket.setLockVersion(0);
        ticket.setCreatedAt(now);
        ticket.setUpdatedAt(now);
        ticketMapper.insert(ticket);
        return toResponse(ticket);
    }

    public PageResult<TicketResponse> listMine(AuthenticatedUser user, long page, long size, TicketStatus status) {
        Page<SupportTicket> result = ticketMapper.selectPage(Page.of(page, size),
                new LambdaQueryWrapper<SupportTicket>()
                        .eq(SupportTicket::getUserId, user.userId())
                        .eq(status != null, SupportTicket::getStatus, status)
                        .orderByDesc(SupportTicket::getCreatedAt));
        return new PageResult<>(page, size, result.getTotal(), result.getRecords().stream().map(this::toResponse).toList());
    }

    public PageResult<TicketResponse> list(long page, long size, TicketStatus status) {
        Page<SupportTicket> result = ticketMapper.selectPage(Page.of(page, size),
                new LambdaQueryWrapper<SupportTicket>()
                        .eq(status != null, SupportTicket::getStatus, status)
                        .orderByDesc(SupportTicket::getCreatedAt));
        return new PageResult<>(page, size, result.getTotal(), result.getRecords().stream().map(this::toResponse).toList());
    }

    public TicketResponse getMine(AuthenticatedUser user, Long id) {
        return toResponse(requireMine(user, id));
    }

    public TicketResponse get(Long id) {
        return toResponse(require(id));
    }

    @Transactional
    public TicketResponse updateStatus(AuthenticatedUser admin, Long id, TicketStatus next, String note, String resolution, Integer lockVersion) {
        SupportTicket ticket = require(id);
        if (lockVersion == null || !lockVersion.equals(ticket.getLockVersion())) {
            throw new BusinessException(HttpStatus.CONFLICT, "工单已被其他管理员修改，请刷新后重试");
        }
        if (!ticket.getStatus().canTransitTo(next)) {
            throw new BusinessException("工单状态不允许从 " + ticket.getStatus() + " 变更为 " + next);
        }
        if (next == TicketStatus.RESOLVED && (resolution == null || resolution.isBlank())) {
            throw new BusinessException("解决工单时必须填写处理结果");
        }
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime resolvedAt = ((next == TicketStatus.RESOLVED || next == TicketStatus.CLOSED) && ticket.getResolvedAt() == null)
                ? now
                : ticket.getResolvedAt();
        int updated = ticketMapper.update(null, new LambdaUpdateWrapper<SupportTicket>()
                .eq(SupportTicket::getId, id)
                .eq(SupportTicket::getLockVersion, lockVersion)
                .set(SupportTicket::getStatus, next)
                .set(SupportTicket::getHandlerId, admin.userId())
                .set(SupportTicket::getHandlingNote, note)
                .set(SupportTicket::getResolution, resolution)
                .set(SupportTicket::getResolvedAt, resolvedAt)
                .set(SupportTicket::getUpdatedAt, now)
                .set(SupportTicket::getLockVersion, lockVersion + 1));
        if (updated != 1) {
            throw new BusinessException(HttpStatus.CONFLICT, "工单已被其他管理员修改，请刷新后重试");
        }
        TicketOperationLog log = new TicketOperationLog();
        log.setTicketId(id);
        log.setOperatorId(admin.userId());
        log.setPreviousStatus(ticket.getStatus().name());
        log.setNextStatus(next.name());
        log.setOperationNote(note);
        log.setCreatedAt(now);
        operationLogMapper.insert(log);
        return toResponse(require(id));
    }

    private SupportTicket requireMine(AuthenticatedUser user, Long id) {
        SupportTicket ticket = require(id);
        if (ticket.getUserId() != null && !ticket.getUserId().equals(user.userId())) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "不能访问其他用户的工单");
        }
        return ticket;
    }

    private SupportTicket require(Long id) {
        SupportTicket ticket = ticketMapper.selectById(id);
        if (ticket == null) {
            throw new BusinessException("工单不存在");
        }
        return ticket;
    }

    private TicketResponse toResponse(SupportTicket ticket) {
        return new TicketResponse(ticket.getId(), ticket.getUserId(), ticket.getTicketNo(), ticket.getConversationId(), ticket.getCategory(),
                ticket.getDescription(), ticket.getContact(), ticket.getStatus(), ticket.getHandlerId(), ticket.getPriority(), ticket.getHandlingNote(),
                ticket.getResolution(), ticket.getResolvedAt(), ticket.getLockVersion(), ticket.getCreatedAt(), ticket.getUpdatedAt());
    }
}
