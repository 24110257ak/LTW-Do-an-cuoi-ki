package vn.iotstar.service;

import vn.iotstar.dto.ChatDto;
import vn.iotstar.entity.ChatMessage;
import vn.iotstar.entity.ChatSession;

import java.util.List;

public interface ChatService {
    ChatSession getOrCreateSession(Long customerId, Integer branchId);
    ChatMessage saveMessage(ChatDto.MessageRequest request);
    List<ChatMessage> getMessages(Long sessionId);
    List<ChatSession> getOpenSessionsForBranch(Integer branchId);
}
