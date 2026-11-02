export function quantityWithinStock(value, availableStock) {
    const stock = Math.max(0, Math.floor(Number(availableStock) || 0));
    if (!stock) return 0;
    const number = Number(value);
    return Math.min(stock, Math.max(1, Number.isFinite(number) ? Math.floor(number) : 1));
}
