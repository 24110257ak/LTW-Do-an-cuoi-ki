package vn.iotstar.util;

import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;
import vn.iotstar.entity.User;
import vn.iotstar.repository.UserRepository;

import java.util.Optional;

@Component
public class SecurityUtils {

    private static UserRepository userRepository;

    public SecurityUtils(UserRepository userRepository) {
        SecurityUtils.userRepository = userRepository;
    }

    public static Optional<User> getCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken
                || "anonymousUser".equals(auth.getPrincipal())) {
            return Optional.empty();
        }
        if (auth.getPrincipal() instanceof User user) {
            return Optional.of(user);
        }
        if (userRepository != null && auth.getName() != null && !"anonymousUser".equals(auth.getName())) {
            return userRepository.findByUsername(auth.getName());
        }
        return Optional.empty();
    }

    public static Optional<String> getCurrentUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && !(auth instanceof AnonymousAuthenticationToken)) {
            if (auth.getPrincipal() instanceof User user) {
                return Optional.of(user.getUsername());
            } else if (auth.getPrincipal() instanceof UserDetails ud) {
                return Optional.of(ud.getUsername());
            } else if (auth.getPrincipal() instanceof String s && !"anonymousUser".equals(s)) {
                return Optional.of(s);
            } else if (auth.getName() != null && !"anonymousUser".equals(auth.getName())) {
                return Optional.of(auth.getName());
            }
        }
        return Optional.empty();
    }
}
