export async function request(path, { method = 'GET', body } = {}) {
    const headers = { Accept: 'application/json' };
    if (body !== undefined) {
        headers['Content-Type'] = 'application/json';
        const token = document.querySelector('meta[name="_csrf"]')?.content;
        const header = document.querySelector('meta[name="_csrf_header"]')?.content;
        if (token && header) headers[header] = token;
    }
    const response = await fetch(`/storefront/api${path}`, { method, headers, credentials: 'same-origin',
        body: body === undefined ? undefined : JSON.stringify(body) });
    const data = await response.json().catch(() => ({}));
    if (!response.ok) {
        const error = new Error(data.message || data.detail || 'Không thể tải dữ liệu. Vui lòng thử lại.');
        error.status = response.status;
        throw error;
    }
    return data;
}

export function parameters(state) {
    const params = new URLSearchParams();
    params.set('page', state.page);
    if (state.pageSize) params.set('pageSize', state.pageSize);
    if (state.categoryId) params.set('categoryId', state.categoryId);
    if (state.q) params.set('keyword', state.q);
    if (state.min !== '' && state.min != null) params.set('minPrice', state.min);
    if (state.max !== '' && state.max != null) params.set('maxPrice', state.max);
    if (state.minRating) params.set('minRating', state.minRating);
    params.set('sort', state.sort || 'popular');
    for (const id of state.shopIds || []) params.append('shopIds', id);
    for (const place of state.places || []) params.append('places', place);
    return params;
}
