package VietFreshHub.shop.entity;

import VietFreshHub.auth.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(schema = "dbo", name = "shop_members")
public class ShopMember {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "shop_member_id")
    private Long shopMemberId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "shop_id", nullable = false)
    private Shop shop;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "member_role", nullable = false, length = 30)
    private String memberRole;

    @Column(nullable = false, length = 30)
    private String status;

    protected ShopMember() {
    }

    public Shop getShop() {
        return shop;
    }

    public String getMemberRole() {
        return memberRole;
    }
}
