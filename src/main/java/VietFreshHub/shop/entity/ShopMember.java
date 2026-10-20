package VietFreshHub.shop.entity;

import lombok.Getter;
import lombok.Setter;

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
@Getter
@Setter
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

    public ShopMember() {
    }

    public Shop getShop() {
        return shop;
    }

    public String getMemberRole() {
        return memberRole;
    }

    // Keep scalar-ID queries used by seller management alongside the shared relationship.
    @Column(name = "shop_id", insertable = false, updatable = false)
    private Long shopId;

    public Long getShopId() {
        return shop == null ? shopId : shop.getShopId();
    }

    public void setShopId(Long id) {
        this.shopId = id;
        if (id == null) {
            this.shop = null;
        } else if (this.shop == null || !id.equals(this.shop.getShopId())) {
            this.shop = new Shop();
            this.shop.setShopId(id);
        }
    }

    public void setShop(Shop shop) {
        this.shop = shop;
        this.shopId = shop == null ? null : shop.getShopId();
    }

    // Keep scalar-ID queries used by seller management alongside the shared relationship.
    @Column(name = "user_id", insertable = false, updatable = false)
    private Long userId;

    public Long getUserId() {
        return user == null ? userId : user.getUserId();
    }

    public void setUserId(Long id) {
        this.userId = id;
        if (id == null) {
            this.user = null;
        } else if (this.user == null || !id.equals(this.user.getUserId())) {
            this.user = new User();
            this.user.setUserId(id);
        }
    }

    public void setUser(User user) {
        this.user = user;
        this.userId = user == null ? null : user.getUserId();
    }
}
