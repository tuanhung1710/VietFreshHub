export const $ = id => document.getElementById(id);
export const money = value => Number(value).toLocaleString('vi-VN') + ' VND';
export const discountLabel = percent => Number(percent) === 0 ? 'Giảm dưới 1%' : `-${Number(percent)}%`;
