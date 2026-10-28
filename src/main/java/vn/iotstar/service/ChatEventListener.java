package vn.iotstar.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.*;
import org.springframework.transaction.event.TransactionalEventListener;
import vn.iotstar.dto.ChatDto;
import vn.iotstar.entity.User;
import vn.iotstar.repository.*;
import java.util.*;

@Component @RequiredArgsConstructor @Slf4j
public class ChatEventListener {
    private final ChatSessionRepository sessions;
    private final UserRepository users;
    private final SimpMessagingTemplate broker;

    // Notify only after commit. A new read transaction rechecks current staff assignments.
    @TransactionalEventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public void committed(ChatDto.Event event) {
        try {
            var session = sessions.findById(event.sessionId()).orElse(null);
            if (session == null) return;
            Set<String> recipients = new HashSet<>();
            if (session.getCustomer().isEnabled()) recipients.add(session.getCustomer().getUsername());
            if (Boolean.TRUE.equals(session.getBranch().getIsActive()))
                users.findByBranchBranchIdAndRoleRoleName(session.getBranch().getBranchId(), "STAFF")
                        .stream().filter(User::isEnabled).forEach(u -> recipients.add(u.getUsername()));
            users.findByRoleRoleName("ADMIN").stream().filter(User::isEnabled).forEach(u -> recipients.add(u.getUsername()));
            recipients.forEach(name -> broker.convertAndSendToUser(name, "/queue/chat", event));
        } catch (Exception exception) {
            // History remains available by HTTP even if the realtime broker is unavailable.
            log.warn("Không gửi được thông báo chat phiên {}: {}", event.sessionId(), exception.getMessage());
        }
    }
}
