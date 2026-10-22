package VietFreshHub.admin.service;

import VietFreshHub.admin.dto.AdminAccountRow;
import VietFreshHub.admin.dto.AdminAccountStats;
import VietFreshHub.auth.entity.Role;
import VietFreshHub.auth.entity.User;
import VietFreshHub.auth.entity.UserRole;
import VietFreshHub.auth.entity.UserRoleId;
import VietFreshHub.auth.repository.RoleRepository;
import VietFreshHub.auth.repository.UserExternalAccountRepository;
import VietFreshHub.auth.repository.UserRepository;
import VietFreshHub.auth.repository.UserRoleRepository;
import VietFreshHub.sellerapplication.entity.SellerApplicationStatus;
import VietFreshHub.sellerapplication.repository.SellerApplicationRepository;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class AdminAccountService {

    private static final int PAGE_SIZE = 10;
    private static final String ACTIVE = "ACTIVE";
    private static final String INACTIVE = "INACTIVE";
    private static final String LEGACY_BLOCKED = "BLOCKED";
    private static final String DELETED = "DELETED";
    private static final String ADMIN_ROLE = "ROLE_ADMIN";
    private static final Set<String> ASSIGNABLE_ROLE_NAMES = Set.of(
            "ROLE_CUSTOMER",
            "ROLE_DELIVERY_STAFF",
            "ROLE_STORE_MANAGER"
    );
    private static final String STORE_MANAGER_ROLE = "ROLE_STORE_MANAGER";
    private static final Set<String> DISPLAY_STATUSES = Set.of(ACTIVE, INACTIVE);
    private static final Set<String> CHANGEABLE_STATUSES = Set.of(ACTIVE, INACTIVE);

    private final UserRepository userRepository;
    private final UserRoleRepository userRoleRepository;
    private final UserExternalAccountRepository externalAccountRepository;
    private final RoleRepository roleRepository;
    private final SellerApplicationRepository sellerApplicationRepository;

    public AdminAccountService(
            UserRepository userRepository,
            UserRoleRepository userRoleRepository,
            UserExternalAccountRepository externalAccountRepository,
            RoleRepository roleRepository,
            SellerApplicationRepository sellerApplicationRepository
    ) {
        this.userRepository = userRepository;
        this.userRoleRepository = userRoleRepository;
        this.externalAccountRepository = externalAccountRepository;
        this.roleRepository = roleRepository;
        this.sellerApplicationRepository = sellerApplicationRepository;
    }

    public Page<AdminAccountRow> getAccounts(
            String keyword,
            String status,
            String roleName,
            int requestedPage,
            String sortMode
    ) {
        String normalizedStatus = normalizeStatusFilter(status);
        String normalizedRole = normalizeRoleFilter(roleName);
        int pageNumber = Math.max(0, requestedPage - 1);
        Page<User> users = userRepository.findAll(
                accountFilter(keyword, normalizedStatus, normalizedRole),
                PageRequest.of(pageNumber, PAGE_SIZE, accountSort(sortMode))
        );
        return mapPage(users);
    }

    private Sort accountSort(String sortMode) {
        return switch (sortMode) {
            case "NAME_ASC" -> Sort.by(Sort.Order.asc("fullName"), Sort.Order.asc("userId"));
            case "NAME_DESC" -> Sort.by(Sort.Order.desc("fullName"), Sort.Order.asc("userId"));
            default -> Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("userId"));
        };
    }

    public AdminAccountStats getStats() {
        long activeCount = userRepository.countByStatus(ACTIVE);
        long inactiveCount = userRepository.countByStatus(INACTIVE)
                + userRepository.countByStatus(LEGACY_BLOCKED);
        return new AdminAccountStats(
                activeCount + inactiveCount,
                activeCount,
                inactiveCount
        );
    }

    public List<Role> getRoles() {
        return roleRepository.findAll(Sort.by(Sort.Direction.ASC, "roleName"));
    }

    @Transactional
    public void updateProfile(Long userId, String fullName, String phone) {
        User user = lockUser(userId);
        requireEditable(user);

        String normalizedName = fullName == null ? "" : fullName.trim();
        if (normalizedName.isEmpty() || normalizedName.length() > 150) {
            throw new IllegalArgumentException("Họ tên không được để trống và tối đa 150 ký tự.");
        }

        String normalizedPhone = phone == null || phone.isBlank() ? null : phone.trim();
        if (normalizedPhone != null && normalizedPhone.length() > 30) {
            throw new IllegalArgumentException("Số điện thoại tối đa 30 ký tự.");
        }
        if (normalizedPhone != null
                && userRepository.existsByPhoneAndUserIdNot(normalizedPhone, userId)) {
            throw new IllegalArgumentException("Số điện thoại này đã được tài khoản khác sử dụng.");
        }

        user.setFullName(normalizedName);
        user.setPhone(normalizedPhone);
        userRepository.save(user);
    }

    @Transactional
    public void updateStatus(Long userId, String requestedStatus) {
        User user = lockUser(userId);
        requireEditable(user);
        String newStatus = requestedStatus == null ? "" : requestedStatus.trim().toUpperCase(Locale.ROOT);
        if (!CHANGEABLE_STATUSES.contains(newStatus)) {
            throw new IllegalArgumentException("Chỉ được chuyển trạng thái sang ACTIVE hoặc INACTIVE.");
        }
        if (newStatus.equals(user.getStatus())) {
            return;
        }

        if (ACTIVE.equals(user.getStatus()) && !ACTIVE.equals(newStatus)
                && userRoleRepository.findRoleNamesByUserId(userId).contains(ADMIN_ROLE)
                && userRepository.countOtherUsersWithRoleAndStatus(ACTIVE, ADMIN_ROLE, userId) == 0) {
            throw new IllegalStateException("Không thể ngưng hoạt động admin ACTIVE cuối cùng.");
        }

        user.setStatus(newStatus);
        userRepository.save(user);
    }

    @Transactional
    public void updateRoles(Long userId, List<Integer> requestedRoleIds) {
        User user = lockUser(userId);
        requireEditable(user);
        List<UserRole> currentLinks = userRoleRepository.findAllByUser_UserId(userId);
        Set<Integer> currentIds = currentLinks.stream()
                .map(link -> link.getId().getRoleId())
                .collect(Collectors.toSet());
        Set<String> currentNames = currentLinks.stream()
                .map(link -> link.getRole().getRoleName())
                .collect(Collectors.toSet());

        List<Integer> requestedIds = requestedRoleIds == null ? List.of() : requestedRoleIds;
        Set<Integer> selectedIds = new HashSet<>(requestedIds);
        if (selectedIds.size() != requestedIds.size()) {
            throw new IllegalArgumentException("Danh sách role không hợp lệ.");
        }
        if (selectedIds.isEmpty() && !currentNames.contains(ADMIN_ROLE)) {
            throw new IllegalArgumentException("Tài khoản cần có ít nhất một role.");
        }

        List<Role> selectedRoles = roleRepository.findAllById(selectedIds);
        if (selectedRoles.size() != selectedIds.size()) {
            throw new IllegalArgumentException("Một hoặc nhiều role không còn tồn tại.");
        }

        Set<String> selectedNames = selectedRoles.stream()
                .map(Role::getRoleName)
                .collect(Collectors.toSet());
        if (!ASSIGNABLE_ROLE_NAMES.containsAll(selectedNames)) {
            throw new IllegalArgumentException("Chỉ được chọn các vai trò Khách hàng, Nhân viên giao hàng hoặc Quản lý cửa hàng.");
        }

        // Admin accounts are immutable from the role editor and cannot gain other roles.
        if (currentNames.contains(ADMIN_ROLE)) {
            throw new IllegalStateException("Tài khoản quản trị viên chỉ có vai trò Quản trị viên và không thể điều chỉnh vai trò.");
        }

        if (selectedNames.contains("ROLE_DELIVERY_STAFF") && selectedNames.size() > 1) {
            throw new IllegalArgumentException("Nhân viên giao hàng không thể kết hợp với vai trò khác.");
        }
        if (selectedNames.contains(STORE_MANAGER_ROLE) && !selectedNames.contains("ROLE_CUSTOMER")) {
            throw new IllegalArgumentException("Quản lý cửa hàng chỉ có thể cấp thêm cho tài khoản Khách hàng.");
        }

        if (selectedNames.contains(STORE_MANAGER_ROLE)
                && !currentNames.contains(STORE_MANAGER_ROLE)
                && !sellerApplicationRepository.existsByUser_UserIdAndStatus(
                        userId, SellerApplicationStatus.APPROVED
                )) {
            throw new IllegalStateException("Chỉ cấp vai trò Quản lý cửa hàng sau khi hồ sơ người bán được duyệt.");
        }

        List<UserRole> removedLinks = currentLinks.stream()
                .filter(link -> !selectedIds.contains(link.getId().getRoleId()))
                .toList();
        if (!removedLinks.isEmpty()) {
            userRoleRepository.deleteAll(removedLinks);
        }

        List<UserRole> additions = selectedRoles.stream()
                .filter(role -> !currentIds.contains(role.getRoleId()))
                .map(role -> makeUserRole(user, role))
                .toList();
        if (!additions.isEmpty()) {
            userRoleRepository.saveAll(additions);
        }
    }

    private Specification<User> accountFilter(String keyword, String status, String roleName) {
        String normalizedInput = keyword == null ? "" : keyword.trim().toLowerCase(Locale.ROOT);
        if (!normalizedInput.isEmpty() && normalizedInput.length() > 255) {
            normalizedInput = normalizedInput.substring(0, 255);
        }
        final String normalizedKeyword = normalizedInput;
        final String searchPattern = "%" + normalizedKeyword + "%";
        String selectedStatus = status;
        String selectedRole = roleName;

        return (root, query, builder) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(builder.or(
                    builder.equal(root.get("status"), ACTIVE),
                    builder.equal(root.get("status"), INACTIVE),
                    builder.equal(root.get("status"), LEGACY_BLOCKED)
            ));
            if (!normalizedKeyword.isEmpty()) {
                predicates.add(builder.or(
                        builder.like(builder.lower(root.get("fullName")), searchPattern),
                        builder.like(builder.lower(root.get("email")), searchPattern),
                        builder.like(builder.lower(root.get("phone")), searchPattern)
                ));
            }
            if (!"ALL".equals(selectedStatus)) {
                if (INACTIVE.equals(selectedStatus)) {
                    predicates.add(builder.or(
                            builder.equal(root.get("status"), INACTIVE),
                            builder.equal(root.get("status"), LEGACY_BLOCKED)
                    ));
                } else {
                    predicates.add(builder.equal(root.get("status"), selectedStatus));
                }
            }
            if (!"ALL".equals(selectedRole)) {
                Subquery<Integer> roleQuery = query.subquery(Integer.class);
                Root<UserRole> userRole = roleQuery.from(UserRole.class);
                roleQuery.select(builder.literal(1)).where(
                        builder.equal(userRole.get("user").get("userId"), root.get("userId")),
                        builder.equal(userRole.get("role").get("roleName"), selectedRole)
                );
                predicates.add(builder.exists(roleQuery));
            }
            return builder.and(predicates.toArray(Predicate[]::new));
        };
    }

    private Page<AdminAccountRow> mapPage(Page<User> users) {
        List<Long> userIds = users.getContent().stream().map(User::getUserId).toList();
        Map<Long, List<Integer>> roleIds = new HashMap<>();
        Map<Long, List<String>> roleNames = new HashMap<>();
        Map<Long, List<String>> providers = new HashMap<>();

        if (!userIds.isEmpty()) {
            userRoleRepository.findRoleRowsByUserIds(userIds).forEach(row -> {
                roleIds.computeIfAbsent(row.getUserId(), ignored -> new ArrayList<>()).add(row.getRoleId());
                roleNames.computeIfAbsent(row.getUserId(), ignored -> new ArrayList<>()).add(row.getRoleName());
            });
            externalAccountRepository.findProviderRowsByUserIds(userIds).forEach(row ->
                    providers.computeIfAbsent(row.getUserId(), ignored -> new ArrayList<>()).add(row.getProvider())
            );
        }

        return users.map(user -> new AdminAccountRow(
                user.getUserId(),
                user.getFullName(),
                user.getEmail(),
                user.getPhone(),
                ACTIVE.equals(user.getStatus()) ? ACTIVE : INACTIVE,
                user.getCreatedAt(),
                user.getEmailVerifiedAt(),
                List.copyOf(roleIds.getOrDefault(user.getUserId(), List.of())),
                List.copyOf(roleNames.getOrDefault(user.getUserId(), List.of())),
                List.copyOf(providers.getOrDefault(user.getUserId(), List.of()))
        ));
    }

    private User lockUser(Long userId) {
        return userRepository.findByUserIdForUpdate(userId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy tài khoản."));
    }

    private void requireEditable(User user) {
        if (DELETED.equals(user.getStatus())) {
            throw new IllegalStateException("Tài khoản DELETED chỉ được xem, không thể chỉnh sửa.");
        }
    }

    private String normalizeStatusFilter(String status) {
        if (status == null || status.isBlank() || "ALL".equalsIgnoreCase(status)) {
            return "ALL";
        }
        String normalized = status.trim().toUpperCase(Locale.ROOT);
        return DISPLAY_STATUSES.contains(normalized) ? normalized : "ALL";
    }

    private String normalizeRoleFilter(String roleName) {
        if (roleName == null || roleName.isBlank() || "ALL".equalsIgnoreCase(roleName)) {
            return "ALL";
        }
        String normalized = roleName.trim();
        if (roleRepository.findByRoleName(normalized).isEmpty()) {
            return "ALL";
        }
        return normalized;
    }

    private UserRole makeUserRole(User user, Role role) {
        UserRoleId id = new UserRoleId();
        id.setUserId(user.getUserId());
        id.setRoleId(role.getRoleId());
        UserRole link = new UserRole();
        link.setId(id);
        link.setUser(user);
        link.setRole(role);
        return link;
    }
}
