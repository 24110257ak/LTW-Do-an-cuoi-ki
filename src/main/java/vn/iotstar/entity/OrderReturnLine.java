package vn.iotstar.entity;
import jakarta.persistence.*;import lombok.*;import com.fasterxml.jackson.annotation.JsonIgnore;
@Entity @Table(name="order_return_lines",uniqueConstraints=@UniqueConstraint(columnNames={"receipt_id","order_item_id"})) @Getter @Setter @NoArgsConstructor
public class OrderReturnLine {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long lineId;
 @ManyToOne @JoinColumn(name="receipt_id",nullable=false) @JsonIgnore private OrderReturnReceipt receipt;
 @ManyToOne @JoinColumn(name="order_item_id",nullable=false) private OrderItem item;
 @Column(nullable=false) private int goodQuantity;
 @Column(nullable=false) private int damagedQuantity;
 @Column(nullable=false) private int missingQuantity;
 @Column(length=500,columnDefinition="nvarchar(500)") private String discrepancyNote;
}
