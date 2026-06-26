package org.sspd.servicemgmt.accountingoptions.coaoptions;

public final class AccountCode {
    private AccountCode() {}

    // ── Root Groups ─────────────────────────────────
    public static final String CURRENT_LIABILITIES  = "LIA-001";
    public static final String CURRENT_ASSETS       = "ASS-001";
    public static final String OPERATING_INCOME     = "INC-001";
    public static final String OPERATING_EXPENSES   = "EXP-001";

    // ── Assets ──────────────────────────────────────
    public static final String CASH                 = "ASS-002";  // Cash in Hand
    public static final String BANK_KBZ             = "ASS-003";  // KBZ Bank Account
    public static final String RECEIVABLE           = "ASS-004";  // Accounts Receivable
    public static final String INVENTORY            = "ASS-005";  // Inventory / Stock
    public static final String KPAY                 = "ASS-006";  // KPay
    public static final String WAVE_PAY             = "ASS-007";  // Wave Pay
    public static final String FIXED_ASSETS         = "ASS-008";  // Fixed Assets & Equipment (ပိုင်ဆိုင်မှု)

    // ── Liabilities ─────────────────────────────────
    public static final String PAYABLE              = "LIA-002";  // Accounts Payable
    public static final String SALARY_PAYABLE       = "LIA-003";  // Salary Payable
    public static final String CUSTOMER_ADVANCE     = "LIA-004";  // Customer Advance

    // ── Income ──────────────────────────────────────
    public static final String SALES                = "INC-002";  // Product Sales
    public static final String CONSULTING_REVENUE   = "INC-003";  // Consulting Revenue
    public static final String INVENTORY_GAIN       = "INC-006";  // Inventory Gain
    public static final String PURCHASE_RTN         = "INC-007";  // Purchase Returns & Allowances
    public static final String INVENTORY_OVER       = "INC-008";  // Inventory Over
    public static final String SERVICE_REVENUE = "INC-009"; // id=39
    public static final String COMMISSION      = "INC-010"; // id=40
    public static final String OTHER_INCOME    = "INC-011"; // id=41//  ← new

    // ── Expenses ────────────────────────────────────
    public static final String OFFICE_RENT          = "EXP-002";  // Office Rent
    public static final String ELECTRICITY          = "EXP-003";  // Electricity Bill
    public static final String INTERNET             = "EXP-004";  // Internet Charges
    public static final String COGS                 = "EXP-006";  // Cost of Goods Sold
    public static final String PURCHASES            = "EXP-007";  // Purchases
    public static final String SALES_RTN            = "EXP-010";  // Sales Returns & Allowances
    public static final String INVENTORY_LOSS       = "EXP-011";  // Inventory Loss
    public static final String INVENTORY_SHORT      = "EXP-012";  // Inventory Short  ← rename
    public static final String OPENING_STOCK        = "EXP-013";  // Opening Stock    ← new
    public static final String SALARY              = "EXP-014";
    public static final String TRANSPORTATION      = "EXP-015";
    public static final String MAINTENANCE         = "EXP-016";
    public static final String STATIONERY          = "EXP-017";
    public static final String MARKETING           = "EXP-018";
    public static final String MISC_EXPENSE        = "EXP-019";
    public static final String SERVICE_REPAIR      = "EXP-020";  // Tool ဝယ်ခြင်း / အပြင်ပြင်ကုန်ကျ
    public static final String LOAN_INTEREST       = "EXP-021";  // ချေးငွေအတိုး (Monthly)
    public static final String PHONE_BILL          = "EXP-022";  // ဖုန်းဘိုး
    public static final String TAX_EXPENSE         = "EXP-023";  // အခွန်
    public static final String PETROL_FUEL         = "EXP-024";  // ဆီစရိတ်
    public static final String DECORATION          = "EXP-025";  // ကြော်ငြာ / အလှဆင်
    public static final String STAFF_ALLOWANCE     = "EXP-026";  // ဝန်ထမ်းကြေးငွေ
    public static final String DONATION            = "EXP-027";  // လှူဒါန်းငွေ
    public static final String GENERAL_SALE_EXP    = "EXP-028";  // ရောင်းချစရိတ်

    // ── Equity ──────────────────────────────────────
    public static final String SHARE_CAPITAL        = "EQU-001";  // Share Capital
    public static final String RETAINED_EARNINGS    = "EQU-002";  // Retained Earnings
    public static final String CURRENT_YEAR_PNL     = "EQU-003";  // Current Year Profit/Loss ← new
}
