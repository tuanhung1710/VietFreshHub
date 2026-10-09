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

@Service
@Transactional(readOnly = true)
public class AdminAccountService {

    private static final int PAGE_SIZE = 10;
    private static final String ACTIVE = "ACTIVE";
    private static final String INACTIVE = "INACTIVE";
    private static final String BLOCKED = "BLOCKED";
    private static final String DELETED = "DELETED";
    private static final String ADMIN_ROLE = "ROLE_ADMIN";
    private static final String STORE_MANAGER_ROLE = "ROLE_STORE_MANAGER";
    private static final Set<String> DISPLAY_STATUSES = Set.of(ACTIVE, INACTIVE, BLOCKED, DELETED);
    private static final Set<String> CHANGEABLE_STATUSES = Set.of(ACTIVE, INACTIVE, BLOCKED);

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
            int requestedPage
    ) {
        String normalizedStatus = normalizeStatusFilter(status);
        String normalizedRole = normalizeRoleFilter(roleName);
        int pageNumber = Math.max(0, requestedPage - 1);
        Page<User> users = userRepository.findAll(
                accountFilter(keyword, normalizedStatus, normalizedRole),
                PageRequest.of(pageNumber, PAGE_SIZE, Sort.by(
                        Sort.Order.desc("createdAt"), Sort.Order.desc("userId")
                ))
        );
        return mapPage(users);
    }

    public AdminAccountStats getStats() {
        return new AdminAccountStats(
                userRepository.count(),
                userRepository.countByStatus(ACTIVE),
                userRepository.countByStatus(INACTIVE),
                userRepository.countByStatus(BLOCKED),
                userRepository.countByStatus(DELETED)
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
            throw new IllegalArgumentException("Chỉ được chuyển trạng thái sang ACTIVE, INACTIVE hoặc BLOCKED.");
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
        if (requestedRoleIds == null || requestedRoleIds.isEmpty()) {
            throw new IllegalArgumentException("Tài khoản cần có ít nhất một role.");
        }

        Set<Integer> selectedIds = new HashSet<>(requestedRoleIds);
        if (selectedIds.size() != requestedRoleIds.size()) {
            throw new IllegalArgumentException("Danh sách role không hợp lệ.");
        }

        List<Role> selectedRoles = roleRepository.findAllById(selectedIds);
        if (selectedRoles.size() != selectedIds.size()) {
            throw new IllegalArgumentException("Một hoặc nhiều role không còn tồn tại.");
        }

        List<UserRole> currentLinks = userRoleRepository.findAllByUser_UserId(userId);
        Set<Integer> currentIds = currentLinks.stream().map(link -> link.getId().getRoleId()).collect(java.util.stream.Collectors.toSet());
        Set<String> currentNames = currentLinks.stream().map(link -> link.getRole().getRoleName()).collect(java.util.stream.Collectors.toSet());
        Set<String> selectedNames = selectedRoles.stream().map(Role::getRoleName).collect(java.util.stream.Collectors.toSet());

        if (selectedNames.contains(STORE_MANAGER_ROLE)
                && !currentNames.contains(STORE_MANAGER_ROLE)
                && !sellerApplicationRepository.existsByUser_UserIdAndStatus(
                        userId, SellerApplicationStatus.APPROVED
                )) {
            throw new IllegalStateException("Role STORE_MANAGER chỉ được cấp sau khi hồ sơ người bán được phê duyệt.");
        }

        if (ACTIVE.equals(user.getStatus())
                && currentNames.contains(ADMIN_ROLE)
                && !selectedNames.contains(ADMIN_ROLE)
                && userRepository.countOtherUsersWithRoleAndStatus(ACTIVE, ADMIN_ROLE, userId) == 0) {
            throw new IllegalStateException("Không thể gỡ role admin của admin ACTIVE cuối cùng.");
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
            if (!normalizedKeyword.isEmpty()) {
                predicates.add(builder.or(
                        builder.like(builder.lower(root.get("fullName")), searchPattern),
                        builder.like(builder.lower(root.get("email")), searchPattern),
                        builder.like(builder.lower(root.get("phone")), searchPattern)
                ));
            }
            if (!"ALL".equals(selectedStatus)) {
                predicates.add(builder.equal(root.get("status"), selectedStatus));
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
                user.getStatus(),
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
