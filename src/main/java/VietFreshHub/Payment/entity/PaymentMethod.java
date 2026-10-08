package VietFreshHub.Payment.entity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Nationalized;
@Entity @Table(schema="dbo", name="payment_methods")
@Getter @Setter @NoArgsConstructor
public class PaymentMethod {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) @Column(name="payment_method_id") private Integer paymentMethodId;
    @Nationalized @Column(nullable=false, length=100, unique=true) private String name;
    @Column(name="method_code", nullable=false, length=50, unique=true) private String methodCode;
    @Column(nullable=false, length=20) private String status="ACTIVE";
}
