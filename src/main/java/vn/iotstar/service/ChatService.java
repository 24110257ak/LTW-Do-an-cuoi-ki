package vn.iotstar.service;

import vn.iotstar.dto.ChatDto;
import vn.iotstar.entity.User;

public interface ChatService {
    User requireUser(String username);
    ChatDto.SessionResponse openSession(String username, Integer branchId);
    ChatDto.SessionPage listSessions(String username, Integer branchId, int page);
    ChatDto.SessionResponse getSession(String username, Long sessionId);
    ChatDto.ConversationPage listConversations(String username, Integer branchId, int page);
    ChatDto.SessionResponse getConversation(String username, Long sessionId);
    default ChatDto.MessagePage getMessages(String username, Long sessionId, Long beforeId) {
        return getMessages(username, sessionId, beforeId, null);
    }
    ChatDto.MessagePage getMessages(String username, Long sessionId, Long beforeId, Long afterId);
    ChatDto.MessagePage getConversationMessages(String username, Long sessionId, Long beforeId, Long afterId);
    ChatDto.MessageResponse sendMessage(String username, ChatDto.MessageRequest request);
    ChatDto.SessionResponse claimSession(String username, Long sessionId);
    ChatDto.SessionResponse closeSession(String username, Long sessionId);
    void markRead(String username, Long sessionId, Long messageId);
}
