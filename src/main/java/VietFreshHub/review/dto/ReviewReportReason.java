package VietFreshHub.review.dto;

public enum ReviewReportReason {
    OFFENSIVE("Nội dung xúc phạm hoặc công kích"),
    SPAM("Nội dung spam hoặc quảng cáo"),
    MISLEADING("Thông tin sai lệch hoặc không liên quan"),
    POLICY("Nội dung vi phạm chính sách"),
    OTHER("Lý do khác");

    private final String label;

    ReviewReportReason(String label) { this.label = label; }

    public String getLabel() { return label; }
}
