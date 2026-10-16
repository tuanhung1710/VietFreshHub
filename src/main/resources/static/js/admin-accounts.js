(() => {
    const page = document.querySelector('[data-accounts-page]');
    if (!page) return;

    const backdrop = document.getElementById('modal-backdrop');
    const modalContent = document.getElementById('modal-content');
    const modalTitle = document.getElementById('modal-title');
    const modalEyebrow = document.getElementById('modal-eyebrow');
    const modalFooter = document.getElementById('modal-footer');
    const csrfToken = document.querySelector('meta[name="_csrf"]')?.content || '';
    const csrfParameter = document.querySelector('meta[name="_csrf_parameter"]')?.content || '_csrf';
    let toastTimer;

    const escapeHtml = value => String(value ?? '').replace(/[&<>"']/g, char => ({
        '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;'
    })[char]);

    const roleOptions = [...document.querySelectorAll('[data-role-option]')].map(option => ({
        id: option.dataset.roleId,
        name: option.dataset.roleName,
        label: option.textContent.trim(),
        description: option.dataset.roleDescription || ''
    }));
    const roleLabels = new Map(roleOptions.map(role => [role.name, role.label]));
    const displayRoleName = roleName => roleLabels.get(roleName) || roleName;

    function showToast(message) {
        const toast = document.getElementById('toast');
        toast.textContent = message;
        toast.classList.add('show');
        clearTimeout(toastTimer);
        toastTimer = setTimeout(() => toast.classList.remove('show'), 2800);
    }

    function closeModal() {
        if (backdrop) backdrop.hidden = true;
    }

    function accountFromRow(row) {
        return {
            id: row.dataset.id,
            name: row.dataset.name || '',
            email: row.dataset.email || '',
            phone: row.dataset.phone || '',
            status: row.dataset.status || '',
            created: row.dataset.created || '',
            verified: row.dataset.verified === 'true',
            methods: row.dataset.methods || 'Email và mật khẩu',
            roleIds: row.dataset.roleIds ? row.dataset.roleIds.split(',').filter(Boolean) : [],
            roleNames: row.dataset.roleNames || '',
            profileUrl: row.dataset.profileUrl,
            rolesUrl: row.dataset.rolesUrl,
            statusUrl: row.dataset.statusUrl
        };
    }

    function openModal(account, eyebrow, title, formHtml, actionUrl, actions = []) {
        if (!backdrop || !modalContent || !modalTitle || !modalEyebrow || !modalFooter) {
            showToast('Không thể mở thao tác vì thiếu thành phần hộp thoại. Hãy tải lại trang.');
            return;
        }

        modalEyebrow.textContent = eyebrow;
        modalTitle.textContent = title;
        modalContent.innerHTML = formHtml;
        if (actionUrl) {
            const form = modalContent.querySelector('form');
            if (!form) {
                showToast('Không tìm thấy biểu mẫu thao tác. Hãy tải lại trang.');
                return;
            }
            form.action = actionUrl;
            form.insertAdjacentHTML('afterbegin', `<input type="hidden" name="${escapeHtml(csrfParameter)}" value="${escapeHtml(csrfToken)}">`);
            modalFooter.innerHTML = `<button class="button" type="button" data-close-modal>Hủy</button><button class="button primary" type="submit" form="account-action-form">${escapeHtml(actions[0] || 'Lưu')}</button>`;
        } else {
            modalFooter.innerHTML = `<button class="button" type="button" data-close-modal>Đóng</button>${actions.map(action => `<button class="button ${action.primary ? 'primary' : ''}" type="button" data-modal-action="${action.name}">${escapeHtml(action.label)}</button>`).join('')}`;
        }
        backdrop.hidden = false;
        backdrop.querySelector('.icon-button')?.focus();
    }

    function openDetails(account) {
        const statusText = account.status === 'ACTIVE' ? 'Active'
            : account.status === 'INACTIVE' ? 'Inactive'
                : account.status === 'BLOCKED' ? 'Blocked' : 'Deleted · chỉ xem';
        const content = `
            <div class="detail-hero"><span class="account-avatar">${escapeHtml(account.name.trim().split(/\s+/).slice(-2).map(part => part[0] || '').join('').toUpperCase())}</span>
                <div><h3>${escapeHtml(account.name)}</h3><p>${escapeHtml(account.email)}</p></div></div>
            <div class="detail-grid">
                <div class="detail-item"><span>Số điện thoại</span><strong>${escapeHtml(account.phone || 'Chưa cập nhật')}</strong></div>
                <div class="detail-item"><span>Trạng thái</span><strong>${escapeHtml(statusText)}</strong></div>
                <div class="detail-item"><span>Email xác minh</span><strong>${account.verified ? 'Đã xác minh' : 'Chưa xác minh'}</strong></div>
                <div class="detail-item"><span>Đăng nhập qua</span><strong>${escapeHtml(account.methods)}</strong></div>
                <div class="detail-item"><span>Ngày tạo</span><strong>${escapeHtml(account.created)}</strong></div>
                <div class="detail-item"><span>Mã tài khoản</span><strong>#${escapeHtml(account.id)}</strong></div>
            </div>
            <p class="detail-section-label">VAI TRÒ HIỆN CÓ</p>
            <div class="role-list">${account.roleNames ? account.roleNames.split(', ').map(role => `<span class="role-chip">${escapeHtml(displayRoleName(role))}</span>`).join('') : '<span class="muted">Chưa có vai trò</span>'}</div>`;
        const actions = account.status === 'DELETED' ? [] : [
            { name: 'edit', label: 'Sửa hồ sơ' },
            { name: 'roles', label: 'Đổi vai trò', primary: true },
            { name: 'status', label: 'Đổi trạng thái' }
        ];
        openModal(account, `MÃ TÀI KHOẢN #${account.id}`, 'Chi tiết tài khoản', content, null, actions);
        if (backdrop) backdrop.dataset.accountId = account.id;
    }

    function openProfile(account) {
        const content = `
            <form id="account-action-form" method="post">
                <div class="field"><label for="full-name">Họ và tên</label><input id="full-name" name="fullName" maxlength="150" required value="${escapeHtml(account.name)}"></div>
                <div class="field"><label for="phone">Số điện thoại</label><input id="phone" name="phone" maxlength="30" value="${escapeHtml(account.phone)}"></div>
                <p class="muted">Email đăng nhập được giữ nguyên.</p>
            </form>`;
        openModal(account, `MÃ TÀI KHOẢN #${account.id}`, 'Chỉnh sửa hồ sơ', content, account.profileUrl, ['Lưu hồ sơ']);
    }

    function openRoles(account) {
        const selectedIds = new Set(account.roleIds);
        const options = roleOptions.map(role => `
            <label class="role-option"><input type="checkbox" name="roleIds" value="${escapeHtml(role.id)}" ${selectedIds.has(role.id) ? 'checked' : ''}>
                <span><strong>${escapeHtml(role.label)}</strong><small>${escapeHtml(role.description)}</small></span></label>`).join('');
        const content = `<form id="account-action-form" method="post"><div class="role-options">${options}</div>
            <p class="inline-warning">Vai trò Quản lý cửa hàng chỉ được cấp cho tài khoản có hồ sơ người bán đã được phê duyệt. Tài khoản đã xóa chỉ đọc.</p></form>`;
        openModal(account, `MÃ TÀI KHOẢN #${account.id}`, 'Điều chỉnh vai trò', content, account.rolesUrl, ['Lưu vai trò']);
    }

    function openStatus(account) {
        const statuses = [
            ['ACTIVE', 'Active', 'Có thể đăng nhập và sử dụng hệ thống.'],
            ['INACTIVE', 'Inactive', 'Tạm ngưng hoạt động.'],
            ['BLOCKED', 'Blocked', 'Bị chặn đăng nhập bởi quản trị viên.']
        ];
        const choices = statuses.map(([value, label, description]) => `
            <label class="status-choice"><input type="radio" name="status" value="${value}" ${account.status === value ? 'checked' : ''}>
                <span><strong>${label}</strong><small>${description}</small></span></label>`).join('');
        const content = `<form id="account-action-form" method="post">${choices}</form>`;
        openModal(account, `MÃ TÀI KHOẢN #${account.id}`, 'Cập nhật trạng thái', content, account.statusUrl, ['Cập nhật']);
    }

    document.addEventListener('click', event => {
        if (!(event.target instanceof Element)) return;

        const button = event.target.closest('[data-action]');
        if (!button) return;
        const row = button.closest('tr');
        if (!row?.dataset.id) {
            showToast('Không tìm thấy dữ liệu tài khoản cho thao tác này. Hãy tải lại trang để thử lại.');
            return;
        }

        const account = accountFromRow(row);
        if (button.dataset.action === 'details') openDetails(account);
        if (button.dataset.action === 'edit') openProfile(account);
        if (button.dataset.action === 'roles') openRoles(account);
        if (button.dataset.action === 'status') openStatus(account);
    });

    backdrop?.addEventListener('click', event => {
        if (!(event.target instanceof Element)) return;

        if (event.target === backdrop || event.target.closest('[data-close-modal]')) {
            closeModal();
            return;
        }
        const actionButton = event.target.closest('[data-modal-action]');
        if (!actionButton) return;
        const row = [...document.querySelectorAll('tbody tr[data-id]')]
            .find(candidate => candidate.dataset.id === backdrop.dataset.accountId);
        if (!row) {
            closeModal();
            showToast('Không tìm thấy tài khoản. Hãy tải lại trang để cập nhật danh sách.');
            return;
        }

        const account = accountFromRow(row);
        if (actionButton.dataset.modalAction === 'edit') openProfile(account);
        if (actionButton.dataset.modalAction === 'roles') openRoles(account);
        if (actionButton.dataset.modalAction === 'status') openStatus(account);
    });

    backdrop?.addEventListener('keydown', event => {
        if (event.key === 'Escape') closeModal();
    });
    document.addEventListener('keydown', event => {
        if ((event.ctrlKey || event.metaKey) && event.key.toLowerCase() === 'k') {
            event.preventDefault();
            document.getElementById('search-input')?.focus();
        }
    });
})();
