package org.sspd.servicemgmt.rbacoptions.permissionoptions.enums;

import lombok.Getter;

@Getter
public enum PermissionName {

    // User Management Permissions (အသုံးပြုသူ စီမံခန့်ခွဲမှု)
    CAN_ACCESS_USERS_READ("အသုံးပြုသူစာရင်းအားလုံးကို ကြည့်ရှုခွင့်ပြုသည်"),
    CAN_ACCESS_USER_CREATE("အသုံးပြုသူအကောင့်အသစ်များ ဖန်တီးခွင့်ပြုသည်"),
    CAN_ACCESS_USER_UPDATE("ရှိပြီးသား အသုံးပြုသူအချက်အလက်များကို ပြင်ဆင်ခွင့်ပြုသည်"),
    CAN_ACCESS_USER_DELETE("အသုံးပြုသူအကောင့်များကို ဖျက်သိမ်းခွင့်ပြုသည်"),
    CAN_ACCESS_USER_ASSIGN_ROLES("အသုံးပြုသူအား Role (ရာထူးတာဝန်) သတ်မှတ်ခွင့်ပြုသည်"),
    CAN_ACCESS_USER_REMOVE_ROLES("အသုံးပြုသူထံမှ Role (ရာထူးတာဝန်) ပြန်လည်ရုပ်သိမ်းခွင့်ပြုသည်"),

    // Role Management Permissions (ရာထူးတာဝန် စီမံခန့်ခွဲမှု)
    CAN_ACCESS_ROLES_READ("Role (ရာထူးတာဝန်) စာရင်းအားလုံးကို ကြည့်ရှုခွင့်ပြုသည်"),
    CAN_ACCESS_ROLE_CREATE("Role (ရာထူးတာဝန်) အသစ်များ ဖန်တီးခွင့်ပြုသည်"),
    CAN_ACCESS_ROLE_UPDATE("ရှိပြီးသား Role (ရာထူးတာဝန်) များကို ပြင်ဆင်ခွင့်ပြုသည်"),
    CAN_ACCESS_ROLE_DELETE("Role (ရာထူးတာဝန်) များကို ဖျက်သိမ်းခွင့်ပြုသည်"),
    CAN_ACCESS_ROLE_ASSIGN_PERMISSIONS("Role များအား လုပ်ပိုင်ခွင့် (Permission) သတ်မှတ်ခွင့်ပြုသည်"),
    CAN_ACCESS_ROLE_REMOVE_PERMISSIONS("Role များထံမှ လုပ်ပိုင်ခွင့် (Permission) ပြန်လည်ဖယ်ရှားခွင့်ပြုသည်"),

    // Brand Management
    CAN_ACCESS_BRAND_CREATE("တံဆိပ် (Brand) အသစ်များ ဖန်တီးခွင့်ပြုသည်"),
    CAN_ACCESS_BRAND_READ("တံဆိပ် (Brand) စာရင်းအားလုံးကို ကြည့်ရှုခွင့်ပြုသည်"),
    CAN_ACCESS_BRAND_UPDATE("ရှိပြီးသား တံဆိပ် (Brand) များကို ပြင်ဆင်ခွင့်ပြုသည်"),
    CAN_ACCESS_BRAND_DELETE("တံဆိပ် (Brand) များကို ဖျက်သိမ်းခွင့်ပြုသည်"),

    // Category Management
    CAN_ACCESS_CATEGORY_CREATE("အမျိုးအစား (Category) အသစ်များ ဖန်တီးခွင့်ပြုသည်"),
    CAN_ACCESS_CATEGORY_READ("အမျိုးအစား (Category) စာရင်းအားလုံးကို ကြည့်ရှုခွင့်ပြုသည်"),
    CAN_ACCESS_CATEGORY_UPDATE("ရှိပြီးသား အမျိုးအစား (Category) များကို ပြင်ဆင်ခွင့်ပြုသည်"),
    CAN_ACCESS_CATEGORY_DELETE("အမျိုးအစား (Category) များကို ဖျက်သိမ်းခွင့်ပြုသည်"),

    // Unit Management
    CAN_ACCESS_UNIT_CREATE("ယူနစ် (Unit) အသစ်များ ဖန်တီးခွင့်ပြုသည်"),
    CAN_ACCESS_UNIT_READ("ယူနစ် (Unit) စာရင်းအားလုံးကို ကြည့်ရှုခွင့်ပြုသည်"),
    CAN_ACCESS_UNIT_UPDATE("ရှိပြီးသား ယူနစ် (Unit) များကို ပြင်ဆင်ခွင့်ပြုသည်"),
    CAN_ACCESS_UNIT_DELETE("ယူနစ် (Unit) များကို ဖျက်သိမ်းခွင့်ပြုသည်"),

    // Product Management
    CAN_ACCESS_PRODUCT_CREATE("ကုန်ပစ္စည်း (Product) အသစ်များ ဖန်တီးခွင့်ပြုသည်"),
    CAN_ACCESS_PRODUCT_READ("ကုန်ပစ္စည်း (Product) စာရင်းအားလုံးကို ကြည့်ရှုခွင့်ပြုသည်"),
    CAN_ACCESS_PRODUCT_UPDATE("ရှိပြီးသား ကုန်ပစ္စည်း (Product) များကို ပြင်ဆင်ခွင့်ပြုသည်"),
    CAN_ACCESS_PRODUCT_DELETE("ကုန်ပစ္စည်း (Product) များကို ဖျက်သိမ်းခွင့်ပြုသည်"),
    CAN_ACCESS_PRODUCT_PRICE_HISTORY_READ("ကုန်ပစ္စည်း Price History နှင့် Weighted Average Cost ကို ကြည့်ရှုခွင့်ပြုသည်"),
    CAN_ACCESS_PHYSICAL_STOCK_COUNT("Physical Stock Count ပြုလုပ်ခွင့်ပြုသည်"),

    // Product Serial Management
    CAN_ACCESS_PRODUCT_SERIAL_CREATE("ကုန်ပစ္စည်း Serial Number အသစ်များ ဖန်တီးခွင့်ပြုသည်"),
    CAN_ACCESS_PRODUCT_SERIAL_READ("ကုန်ပစ္စည်း Serial Number စာရင်းအားလုံးကို ကြည့်ရှုခွင့်ပြုသည်"),
    CAN_ACCESS_PRODUCT_SERIAL_UPDATE("ရှိပြီးသား Serial Number များကို ပြင်ဆင်ခွင့်ပြုသည်"),
    CAN_ACCESS_PRODUCT_SERIAL_DELETE("Serial Number များကို ဖျက်သိမ်းခွင့်ပြုသည်"),

    // Supplier Management
    CAN_ACCESS_SUPPLIER_CREATE("ကုန်သွင်းသူ (Supplier) အသစ်များ ဖန်တီးခွင့်ပြုသည်"),
    CAN_ACCESS_SUPPLIER_READ("ကုန်သွင်းသူ (Supplier) စာရင်းအားလုံးကို ကြည့်ရှုခွင့်ပြုသည်"),
    CAN_ACCESS_SUPPLIER_UPDATE("ရှိပြီးသား ကုန်သွင်းသူ (Supplier) အချက်အလက်များကို ပြင်ဆင်ခွင့်ပြုသည်"),
    CAN_ACCESS_SUPPLIER_DELETE("ကုန်သွင်းသူ (Supplier) များကို ဖျက်သိမ်းခွင့်ပြုသည်"),

    // COA (Chart of Accounts) Management
    CAN_ACCESS_COA_CREATE("စာရင်းခေါင်းစဉ် (COA) အသစ်များ ဖန်တီးခွင့်ပြုသည်"),
    CAN_ACCESS_COA_READ("စာရင်းခေါင်းစဉ် (COA) စာရင်းအားလုံးကို ကြည့်ရှုခွင့်ပြုသည်"),
    CAN_ACCESS_COA_UPDATE("ရှိပြီးသား စာရင်းခေါင်းစဉ် (COA) များကို ပြင်ဆင်ခွင့်ပြုသည်"),
    CAN_ACCESS_COA_DELETE("စာရင်းခေါင်းစဉ် (COA) များကို ဖျက်သိမ်းခွင့်ပြုသည်"),

    // Payment Method Management
    CAN_ACCESS_PAYMENT_METHOD_CREATE("ငွေပေးချေမှုစနစ် (Payment Method) အသစ်များ ဖန်တီးခွင့်ပြုသည်"),
    CAN_ACCESS_PAYMENT_METHOD_READ("ငွေပေးချေမှုစနစ် (Payment Method) စာရင်းများကို ကြည့်ရှုခွင့်ပြုသည်"),
    CAN_ACCESS_PAYMENT_METHOD_UPDATE("ရှိပြီးသား ငွေပေးချေမှုစနစ်များကို ပြင်ဆင်ခွင့်ပြုသည်"),
    CAN_ACCESS_PAYMENT_METHOD_DELETE("ငွေပေးချေမှုစနစ်များကို ဖျက်သိမ်းခွင့်ပြုသည်"),

    // Customer Management
    CAN_ACCESS_CUSTOMER_CREATE("ဝယ်ယူသူ (Customer) အသစ်များ ဖန်တီးခွင့်ပြုသည်"),
    CAN_ACCESS_CUSTOMER_READ("ဝယ်ယူသူ (Customer) စာရင်းများကို ကြည့်ရှုခွင့်ပြုသည်"),
    CAN_ACCESS_CUSTOMER_UPDATE("ရှိပြီးသား ဝယ်ယူသူအချက်အလက်များကို ပြင်ဆင်ခွင့်ပြုသည်"),
    CAN_ACCESS_CUSTOMER_DELETE("ဝယ်ယူသူ (Customer) စာရင်းများကို ဖျက်သိမ်းခွင့်ပြုသည်"),

    CAN_ACCESS_STAFF_CREATE("ဝယ်ယူသူ (Customer) အသစ်များ ဖန်တီးခွင့်ပြုသည်"),
    CAN_ACCESS_STAFF_READ("ဝယ်ယူသူ (Customer) စာရင်းများကို ကြည့်ရှုခွင့်ပြုသည်"),
    CAN_ACCESS_STAFF_UPDATE("ရှိပြီးသား ဝယ်ယူသူအချက်အလက်များကို ပြင်ဆင်ခွင့်ပြုသည်"),
    CAN_ACCESS_STAFF_DELETE("ဝယ်ယူသူ (Customer) စာရင်းများကို ဖျက်သိမ်းခွင့်ပြုသည်"),

    CAN_ACCESS_JOURNAL_CREATE("ဝယ်ယူသူ (Customer) အသစ်များ ဖန်တီးခွင့်ပြုသည်"),
    CAN_ACCESS_JOURNAL_READ("ဝယ်ယူသူ (Customer) စာရင်းများကို ကြည့်ရှုခွင့်ပြုသည်"),
    CAN_ACCESS_JOURNAL_UPDATE("ရှိပြီးသား ဝယ်ယူသူအချက်အလက်များကို ပြင်ဆင်ခွင့်ပြုသည်"),
    CAN_ACCESS_ACCOUNTING_PERIOD_LOCK("Accounting period ပိတ်/ပြန်ဖွင့်ခွင့်"),

    CAN_ACCESS_PAYMENT_TRANSACTION_CREATE("ဝယ်ယူသူ (Customer) အသစ်များ ဖန်တီးခွင့်ပြုသည်"),
    CAN_ACCESS_PAYMENT_TRANSACTION_READ("ဝယ်ယူသူ (Customer) စာရင်းများကို ကြည့်ရှုခွင့်ပြုသည်"),

    CAN_ACCESS_ACCOUNT_BALANCE_READ("ဝယ်ယူသူ (Customer) အသစ်များ ဖန်တီးခွင့်ပြုသည်"),
    CAN_ACCESS_ACCOUNT_BALANCE_UPDATE("ဝယ်ယူသူ (Customer) အသစ်များ ဖန်တီးခွင့်ပြုသည်"),



    CAN_ACCESS_STOCK_READ("ဝယ်ယူသူ (Customer) အသစ်များ ဖန်တီးခွင့်ပြုသည်"),
    CAN_ACCESS_STOCK_UPDATE("ဝယ်ယူသူ (Customer) အသစ်များ ဖန်တီးခွင့်ပြုသည်"),

    CAN_ACCESS_STOCK_ADJUSTMENT_CREATE("Stock Adjustment ဖန်တီးခွင့်ပြုသည်"),
    CAN_ACCESS_STOCK_ADJUSTMENT_READ("Stock Adjustment များကို ကြည့်ရှုခွင့်ပြုသည်"),
    CAN_ACCESS_STOCK_ADJUSTMENT_UPDATE("Stock Adjustment ပြင်ဆင်ခွင့်ပြုသည်"),
    CAN_ACCESS_STOCK_ADJUSTMENT_DELETE("Stock Adjustment ဖျက်သိမ်းခွင့်ပြုသည်"),

    CAN_ACCESS_EXPENSE_CREATE("Expense ဖန်တီးခွင့်ပြုသည်"),
    CAN_ACCESS_EXPENSE_STAFF_OVERRIDE("Expense အတွက် အခြား Staff ရွေးခွင့်"),
    CAN_ACCESS_EXPENSE_BACKDATE("Expense ကို နောက်ကျသောရက်စွဲဖြင့် သိမ်းခွင့်"),
    CAN_ACCESS_EXPENSE_FUTUREDATE("Expense ကို အနာဂတ်ရက်စွဲဖြင့် သိမ်းခွင့်"),
    CAN_ACCESS_EXPENSE_READ("Expense များကို ကြည့်ရှုခွင့်ပြုသည်"),
    CAN_ACCESS_EXPENSE_UPDATE("Expense များကို ပြင်ဆင်ခွင့်ပြုသည်"),
    CAN_ACCESS_EXPENSE_DELETE("Expense များကို ဖျက်သိမ်းခွင့်ပြုသည်"),

    CAN_ACCESS_INCOME_CREATE("Income ဖန်တီးခွင့်ပြုသည်"),
    CAN_ACCESS_INCOME_STAFF_OVERRIDE("Income အတွက် အခြား Staff ရွေးခွင့်"),
    CAN_ACCESS_INCOME_BACKDATE("Income ကို နောက်ကျသောရက်စွဲဖြင့် သိမ်းခွင့်"),
    CAN_ACCESS_INCOME_FUTUREDATE("Income ကို အနာဂတ်ရက်စွဲဖြင့် သိမ်းခွင့်"),
    CAN_ACCESS_INCOME_READ("Income များကို ကြည့်ရှုခွင့်ပြုသည်"),
    CAN_ACCESS_INCOME_UPDATE("Income များကို ပြင်ဆင်ခွင့်ပြုသည်"),
    CAN_ACCESS_INCOME_DELETE("Income များကို ဖျက်သိမ်းခွင့်ပြုသည်"),

    CAN_ACCESS_PURCHASE_CREATE("ဝယ်ယူသူ (Customer) အသစ်များ ဖန်တီးခွင့်ပြုသည်"),
    CAN_ACCESS_PURCHASE_STAFF_OVERRIDE("Purchase အတွက် အခြား Staff ရွေးခွင့်"),
    CAN_ACCESS_PURCHASE_READ("ဝယ်ယူသူ (Customer) အသစ်များ ဖန်တီးခွင့်ပြုသည်"),
    CAN_ACCESS_PURCHASE_UPDATE("ဝယ်ယူမှု (Purchase) အချက်အလက်များကို ပြင်ဆင်ခွင့်ပြုသည်"),
    CAN_ACCESS_PURCHASE_DELETE("Purchase Cancel/Void (ဖျက်သိမ်း/ပြန်လည်စီစဉ်) လုပ်ခွင့်"),

    CAN_ACCESS_PURCHASE_ORDER_CREATE("Purchase Order အသစ် ဖန်တီးခွင့်"),
    CAN_ACCESS_PURCHASE_ORDER_READ("Purchase Order များကို ကြည့်ရှုခွင့်"),
    CAN_ACCESS_PURCHASE_ORDER_UPDATE("Purchase Order များကို ပြင်ဆင်ခွင့်"),
    CAN_ACCESS_PURCHASE_ORDER_DELETE("Purchase Order များကို ဖျက်သိမ်းခွင့်"),
    CAN_ACCESS_PURCHASE_ORDER_RECEIVE("Purchase Order ပစ္စည်းလက်ခံခွင့် (Goods Receipt)"),
    CAN_ACCESS_PURCHASE_ORDER_APPROVE("Purchase Order အတည်ပြု/ပယ်ချခွင့်"),
    CAN_ACCESS_PURCHASE_ORDER_CANCEL_APPROVED("Approved Purchase Order များကို ပယ်ဖျက်ခွင့်"),
    CAN_ACCESS_PURCHASE_ORDER_FINAL_APPROVE("Purchase Order ဒုတိယအဆင့် (Final) အတည်ပြုခွင့်"),

    CAN_ACCESS_PURCHASE_RETURN_CREATE("Purchase Return အသစ် ဖန်တီးခွင့်ပြုသည်"),
    CAN_ACCESS_PURCHASE_RETURN_READ("Purchase Return အချက်အလက်များကို ကြည့်ရှုခွင့်ပြုသည်"),
    CAN_ACCESS_PURCHASE_RETURN_UPDATE("Purchase Return အချက်အလက်များကို ပြင်ဆင်ခွင့်ပြုသည်"),
    CAN_ACCESS_PURCHASE_RETURN_DELETE("Purchase Return အချက်အလက်များကို ဖျက်သိမ်းခွင့်ပြုသည်"),
    CAN_ACCESS_PURCHASE_RETURN_REASON_MANAGE("Purchase Return reason master စီမံခန့်ခွဲခွင့်"),
    CAN_ACCESS_PURCHASE_RETURN_SUBMIT("Purchase Return approval သို့တင်ခွင့်"),
    CAN_ACCESS_PURCHASE_RETURN_APPROVE("Purchase Return အတည်ပြု/ငြင်းပယ်ခွင့်"),
    CAN_ACCESS_PURCHASE_RETURN_DISPATCH("Purchase Return ပစ္စည်းပို့ခွင့်"),
    CAN_ACCESS_PURCHASE_RETURN_RECEIVE("Supplier လက်ခံမှု မှတ်တမ်းတင်ခွင့်"),
    CAN_ACCESS_PURCHASE_RETURN_SETTLE("Purchase Return credit/refund reconciliation ခွင့်"),

    CAN_ACCESS_PURCHASE_RETURN_DETAIL_CREATE("Purchase Return Detail အသစ် ဖန်တီးခွင့်ပြုသည်"),
    CAN_ACCESS_PURCHASE_RETURN_DETAIL_READ("Purchase Return Detail အချက်အလက်များကို ကြည့်ရှုခွင့်ပြုသည်"),
    CAN_ACCESS_PURCHASE_RETURN_DETAIL_UPDATE("Purchase Return Detail အချက်အလက်များကို ပြင်ဆင်ခွင့်ပြုသည်"),
    CAN_ACCESS_PURCHASE_RETURN_DETAIL_DELETE("Purchase Return Detail အချက်အလက်များကို ဖျက်သိမ်းခွင့်ပြုသည်"),


    CAN_ACCESS_SALE_CREATE("ဝယ်ယူသူ (Customer) အသစ်များ ဖန်တီးခွင့်ပြုသည်"),
    CAN_ACCESS_SALE_READ("ဝယ်ယူသူ (Customer) အသစ်များ ဖန်တီးခွင့်ပြုသည်"),
    CAN_ACCESS_SALE_DELETE("ဝယ်ယူမှု (Purchase) အချက်အလက်များကို ပြင်ဆင်ခွင့်ပြုသည်"),
    CAN_ACCESS_SALE_UPDATE("ဝယ်ယူမှု (Purchase) အချက်အလက်များကို ပြင်ဆင်ခွင့်ပြုသည်"),
    CAN_ACCESS_SALE_BACKDATE("Sale ကို နောက်ကျသောရက်စွဲဖြင့် သိမ်းခွင့်"),
    CAN_ACCESS_SALE_FUTUREDATE("Sale ကို အနာဂတ်ရက်စွဲဖြင့် သိမ်းခွင့်"),
    CAN_ACCESS_SALE_STAFF_OVERRIDE("Sale အတွက် အခြား Staff ရွေးခွင့်"),
    CAN_ACCESS_SALE_PRICE_EDIT("Edit sale item unit price"),
    CAN_ACCESS_SALE_DISCOUNT_OVERRIDE("Override role discount limit"),
    CAN_ACCESS_SALE_VOID("Void a sale with a reason"),
    CAN_ACCESS_SALE_RETURN_CREATE("Sale Return ဖန်တီးခွင့်ပြုသည်"),
    CAN_ACCESS_SALE_RETURN_READ("Sale Return အချက်အလက်များကို ကြည့်ရှုခွင့်ပြုသည်"),
    CAN_ACCESS_SALE_RETURN_UPDATE("Sale Return အချက်အလက်များကို ပြင်ဆင်ခွင့်ပြုသည်"),
    CAN_ACCESS_SALE_RETURN_DELETE("Sale Return အချက်အလက်များကို ဖျက်သိမ်းခွင့်ပြုသည်"),

    CAN_ACCESS_CASH_DRAWER_READ("View cash drawer sessions and movements"),
    CAN_ACCESS_CASH_DRAWER_MANAGE("Open, close, cash-in and cash-out a drawer"),

    CAN_ACCESS_QUOTATION_CREATE("Create quotations"),
    CAN_ACCESS_QUOTATION_READ("View quotations"),
    CAN_ACCESS_QUOTATION_UPDATE("Update draft quotations"),
    CAN_ACCESS_QUOTATION_CANCEL("Cancel quotations"),
    CAN_ACCESS_QUOTATION_CONVERT_TO_SALE("Convert accepted quotations to sales"),


    // Credit / AR controls
    CAN_ACCESS_CREDIT_TERM_CREATE("Customer Credit Term ဖန်တီးခွင့်"),
    CAN_ACCESS_CREDIT_TERM_READ("Customer Credit Term ကြည့်ရှုခွင့်"),
    CAN_ACCESS_CREDIT_TERM_HISTORY_READ("Credit Term History ကြည့်ရှုခွင့်"),

    CAN_ACCESS_CREDIT_ALERT_READ("Credit Alert (Overdue/Due Soon/Limit) ကြည့်ရှုခွင့်"),
    CAN_ACCESS_CREDIT_ALERT_RESOLVE("Credit Alert ဖြေရှင်းခွင့်"),

    CAN_ACCESS_CUSTOMER_PAYMENT_CREATE("Customer Payment (Advance/Invoice) ဖန်တီးခွင့်"),
    CAN_ACCESS_CUSTOMER_PAYMENT_READ("Customer Payment ကြည့်ရှုခွင့်"),

    CAN_ACCESS_CREDIT_OVERRIDE_APPROVE("Credit Limit Override (Manager) ခွင့်ပြုခြင်း"),
    CAN_ACCESS_CREDIT_HOLD_UPDATE("Customer Credit Hold ချမှတ်/ဖျက် ခွင့်"),
    CAN_ACCESS_CREDIT_BLACKLIST_UPDATE("Customer Credit Blacklist ချမှတ်/ဖယ်ရှား ခွင့်"),

    // Permission Management Permissions
    CAN_ACCESS_PERMISSIONS_READ("စနစ်အတွင်းရှိ လုပ်ပိုင်ခွင့် (Permission) အားလုံးကို ကြည့်ရှုခွင့်ပြုသည်"),
    CAN_ACCESS_PERMISSION_CREATE("လုပ်ပိုင်ခွင့် (Permission) အသစ်များ ဖန်တီးခွင့်ပြုသည်"),
    CAN_ACCESS_PERMISSION_UPDATE("ရှိပြီးသား လုပ်ပိုင်ခွင့် (Permission) များကို ပြင်ဆင်ခွင့်ပြုသည်"),
    CAN_ACCESS_PERMISSION_DELETE("လုပ်ပိုင်ခွင့် (Permission) များကို ဖျက်သိမ်းခွင့်ပြုသည်"),

    // Service Module Permissions
    CAN_ACCESS_SERVICE_CREATE("Service, Service Type, Sub Service Type အသစ်များ ဖန်တီးခွင့်"),
    CAN_ACCESS_SERVICE_READ("Service, Service Type, Sub Service Type များကို ကြည့်ရှုခွင့်"),
    CAN_ACCESS_SERVICE_UPDATE("Service, Service Type, Sub Service Type များကို ပြင်ဆင်ခွင့်"),
    CAN_ACCESS_SERVICE_DELETE("Service, Service Type, Sub Service Type များကို ဖျက်သိမ်းခွင့်"),

    CAN_ACCESS_SERVICE_JOB_CREATE("Service Job အသစ်များ ဖန်တီးခွင့်"),
    CAN_ACCESS_SERVICE_JOB_READ("Service Job အချက်အလက်များကို ကြည့်ရှုခွင့်"),
    CAN_ACCESS_SERVICE_JOB_UPDATE("Service Job အချက်အလက်များနှင့် status များကို ပြင်ဆင်ခွင့်"),
    CAN_ACCESS_SERVICE_JOB_DELETE("Service Job များကို ဖျက်သိမ်းခွင့်"),
    CAN_ACCESS_SERVICE_JOB_SETTLE("Service Job settlement ပြုလုပ်ခွင့်"),
    CAN_ACCESS_SERVICE_JOB_VOID("Service Job settlement ပြန်ဖျက်ခွင့်"),
    CAN_ACCESS_SERVICE_JOB_PRICE_EDIT("Service Job ဈေးနှုန်းပြင်ဆင်ခွင့်"),
    CAN_ACCESS_SERVICE_JOB_PRICE_OVERRIDE("Min/Max ကျော်သော ဝန်ဆောင်မှုစျေး Manager အတည်ပြုခွင့်"),
    CAN_ACCESS_SERVICE_JOB_REWORK("Service Job rework ဖန်တီးခွင့်"),
    CAN_ACCESS_SERVICE_TECHNICIAN_ASSIGN("ပြုပြင်သူ အားလုံးကို ရွေးခွင့် (မရှိရင် မိမိ Staff သာ)"),

    CAN_ACCESS_BOOKING_CREATE("Booking အသစ်များ ဖန်တီးခွင့်"),
    CAN_ACCESS_BOOKING_READ("Booking အချက်အလက်များကို ကြည့်ရှုခွင့်"),
    CAN_ACCESS_BOOKING_UPDATE("Booking အချက်အလက်များနှင့် status များကို ပြင်ဆင်ခွင့်"),
    CAN_ACCESS_BOOKING_DELETE("Booking များကို ဖျက်သိမ်းခွင့်"),
    CAN_ACCESS_BOOKING_CONVERT_JOB("Booking ကို Service Job သို့ပြောင်းလဲခွင့်"),
    CAN_ACCESS_BOOKING_STAFF_OVERRIDE("ပစ္စည်းလက်ခံမှုအတွက် အခြား Staff ရွေးခွင့်"),

    CAN_ACCESS_BACKUP_SETTINGS_READ("Backup settings ကြည့်ရှုခွင့်"),
    CAN_ACCESS_BACKUP_SETTINGS_UPDATE("Backup settings ပြင်ဆင်ခွင့်"),
    CAN_ACCESS_BACKUP_RUN("Manual backup run ခွင့်"),
    CAN_ACCESS_BACKUP_IMPORT("Backup SQL import/restore ခွင့်"),
    CAN_ACCESS_BACKUP_FILES_READ("Backup file list ကြည့်ရှုခွင့်"),

    CAN_ACCESS_REPORT_READ("အစီရင်ခံစာများကို ကြည့်ရှုခွင့်ပြုသည်"),

    CAN_ACCESS_AUDIT_LOG_READ("Audit log မှတ်တမ်းများ ကြည့်ရှုခွင့်"),

    CAN_ACCESS_PURCHASE_BUDGET("Purchase Budget ကြည့်ရှု / ဖန်တီး / ပြင်ဆင်ခွင့်"),
    CAN_ACCESS_PURCHASE_REORDER("Purchase Reorder suggestions ကြည့်ရှု / အသုံးပြုခွင့်"),
    CAN_ACCESS_PURCHASE_WAREHOUSE("Purchase Warehouse stock balance ကြည့်ရှုခွင့်"),
    CAN_ACCESS_PURCHASE_EXPIRY("Purchase Expiry / FEFO stock lots ကြည့်ရှုခွင့်"),
    CAN_ACCESS_PURCHASE_ANALYTICS("Purchase Analytics ကြည့်ရှုခွင့်"),
    CAN_ACCESS_PURCHASE_IMPORT("Purchase Excel Import / Template အသုံးပြုခွင့်"),

    // Shelf Location Management
    CAN_ACCESS_SHELF_LOCATION_READ("Shelf Location စာရင်းများကို ကြည့်ရှုခွင့်"),
    CAN_ACCESS_SHELF_LOCATION_CREATE("Shelf Location အသစ်ဖန်တီးခွင့်"),
    CAN_ACCESS_SHELF_LOCATION_UPDATE("Shelf Location ပြင်ဆင်ခွင့်"),
    CAN_ACCESS_SHELF_LOCATION_DELETE("Shelf Location ဖျက်သိမ်းခွင့်"),
    CAN_ACCESS_TECHNICIAN_VISIT_START("Technician outdoor visit စတင်/ရောက်/ပိတ်ခွင့်"),
    CAN_ACCESS_TECHNICIAN_LOCATION_READ("Technician live location ကြည့်ခွင့်"),
    CAN_ACCESS_TECHNICIAN_LOCATION_HISTORY_READ("Technician route history ကြည့်ခွင့်"),
    CAN_ACCESS_TECHNICIAN_LOCATION_HISTORY_DELETE("Technician raw GPS history ကို အုပ်ချုပ်သူမှ ဖျက်ခွင့်"),
    CAN_ACCESS_CUSTOMER_LOCATION_UPDATE("Customer GPS location သီးသန့်ပြင်ခွင့်");




    private final String description;


    PermissionName(String description) {
        this.description = description;
    }
}

