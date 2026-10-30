package vn.iotstar.entity;
import jakarta.persistence.*;import lombok.*;import com.fasterxml.jackson.annotation.JsonIgnore;import java.time.LocalDateTime;import java.util.*;
@Entity @Table(name="order_return_receipts") @Getter @Setter @NoArgsConstructor
public class OrderReturnReceipt {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long receiptId;
 @OneToOne @JoinColumn(name="order_id",nullable=false,unique=true) @JsonIgnore private Order order;
 @ManyToOne @JoinColumn(name="inspector_id",nullable=false) private User inspector;
 @Column(nullable=false) private LocalDateTime inspectedAt;
 @Column(length=500,columnDefinition="nvarchar(500)") private String note;
 @OneToMany(mappedBy="receipt",cascade=CascadeType.ALL) private List<OrderReturnLine> lines=new ArrayList<>();
}
