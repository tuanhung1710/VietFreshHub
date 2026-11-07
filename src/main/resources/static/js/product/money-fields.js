"use strict";

// Chạy khi người dùng rời ô nhập, ví dụ bấm sang ô khác hoặc nhấn Tab.
document.addEventListener("focusout", event => {
    const field = event.target;

    // Chỉ xử lý thẻ input có thuộc tính data-vf-money.
    if (!(field instanceof HTMLInputElement) || !field.hasAttribute("data-vf-money")) return;

    // Loại bỏ khoảng trắng ở đầu và cuối để kiểm tra, định dạng số.
    const value = field.value.trim();

    // Chấp nhận số nguyên, số thập phân, dấu +/- và dấu phẩy phân nhóm đúng.
    // Ví dụ hợp lệ: 10000, 10,000, 10000.50.
    // Nếu sai định dạng thì giữ nguyên nội dung để backend kiểm tra khi submit.
    if (!/^[+-]?(?:(?:\d+|\d{1,3}(?:,\d{3})+)(?:\.\d+)?|\.\d+)$/.test(value)) return;

    // Bỏ dấu phẩy đang có rồi tách phần nguyên và phần thập phân bằng dấu chấm.
    // Ví dụ: "1,234,567.50" -> whole = "1234567", fraction = "50".
    // Nếu không có phần thập phân thì fraction mặc định là chuỗi rỗng.
    const [whole, fraction = ""] = value.replaceAll(",", "").split(".");

    // Chèn dấu phẩy để chia phần nguyên thành các nhóm 3 chữ số từ phải sang trái.
    // \B: tránh chèn dấu ở đầu số hoặc ngay sau dấu +/-.
    // (?=(\d{3})+(?!\d)): tìm vị trí có các nhóm đủ 3 chữ số ở bên phải,
    // kết thúc tại cuối phần chữ số.
    // g: thay thế tại tất cả vị trí phù hợp.
    // Ví dụ: "1234567" -> "1,234,567".
    const grouped = whole.replace(/\B(?=(\d{3})+(?!\d))/g, ",");

    // Bỏ các số 0 dư ở cuối phần thập phân, không làm tròn.
    // Ví dụ: "50" -> "5", "00" -> "", "0050" -> "005".
    const decimals = fraction.replace(/0+$/, "");

    // Ghép lại và cập nhật nội dung ô nhập.
    // Nếu phần nguyên rỗng thì dùng "0", ví dụ ".5" -> "0.5".
    // Chỉ thêm dấu chấm nếu phần thập phân còn giá trị.
    // Ví dụ: "10000.50" -> "10,000.5"; "10000.00" -> "10,000".
    // Đây chỉ là định dạng trên giao diện, không gửi request hay lưu database.
    field.value = (grouped || "0") + (decimals ? "." + decimals : "");
});