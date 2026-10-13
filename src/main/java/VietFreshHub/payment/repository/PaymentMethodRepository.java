package VietFreshHub.payment.repository;
import VietFreshHub.payment.entity.PaymentMethod;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
public interface PaymentMethodRepository extends JpaRepository<PaymentMethod,Integer> {
    Optional<PaymentMethod> findByMethodCodeAndStatus(String code,String status);
    Optional<PaymentMethod> findByMethodCode(String code);
}
