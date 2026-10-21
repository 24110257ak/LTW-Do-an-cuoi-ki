package vn.iotstar.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserSummaryResponse {
    private Long userId;
    private String username;
    private String email;
    private String fullName;
    private String phone;
    private String avatarUrl;
    private String role;
    private Boolean isActive;
    private Boolean isVerified;
}
