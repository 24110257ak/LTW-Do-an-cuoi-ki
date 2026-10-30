package vn.iotstar.dto.request;
import jakarta.validation.Valid;import jakarta.validation.constraints.*;import lombok.*;import java.util.*;
@Getter @Setter
public class ReturnInspectionForm {
 @NotEmpty @Valid private List<Line> lines=new ArrayList<>();
 @Size(max=500) private String note;
 @Getter @Setter public static class Line {
  @NotNull private Long orderItemId;
  @NotNull @Min(0) private Integer good;
  @NotNull @Min(0) private Integer damaged;
  @NotNull @Min(0) private Integer missing;
  @Size(max=500) private String reason;
 }
}
