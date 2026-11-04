export const $ = id => document.getElementById(id);
export const money = value => Number(value).toLocaleString('en-US') + ' VND';
export const discountLabel = percent => Number(percent) === 0 ? 'Giảm dưới 1%' : `-${Number(percent)}%`;
