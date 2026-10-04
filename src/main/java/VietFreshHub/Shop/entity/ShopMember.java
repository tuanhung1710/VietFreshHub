package VietFreshHub.Shop.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@Entity
@Table(schema = "dbo", name = "shop_members")
public class ShopMember {

    @Id
    @Column(name = "shop_member_id")
    private Long shopMemberId;

    @Column(name = "shop_id")
    private Long shopId;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "member_role")
    private String memberRole;

    @Column(name = "status")
    private String status;
}
