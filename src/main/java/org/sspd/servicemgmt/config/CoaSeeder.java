package org.sspd.servicemgmt.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.sspd.servicemgmt.accountingoptions.coaoptions.AccountCode;
import org.sspd.servicemgmt.accountingoptions.coaoptions.enums.AccountType;
import org.sspd.servicemgmt.accountingoptions.coaoptions.model.ChartOfAccount;
import org.sspd.servicemgmt.accountingoptions.coaoptions.repository.ChartOfAccountRepository;

@Component
@RequiredArgsConstructor
@Slf4j
@Order(5)
public class CoaSeeder implements CommandLineRunner {

    private final ChartOfAccountRepository repo;

    @Override
    public void run(String... args) {

        // ── Root / Header accounts (no parent) ──────────────────────────────
        seed(AccountCode.CURRENT_ASSETS,      "Current Assets",                    AccountType.Asset,     null);
        seed(AccountCode.CURRENT_LIABILITIES, "Current Liabilities",               AccountType.Liability, null);
        seed(AccountCode.OPERATING_INCOME,    "Operating Income",                  AccountType.Income,    null);
        seed(AccountCode.OPERATING_EXPENSES,  "Operating Expenses",                AccountType.Expense,   null);

        // Equity root accounts (standalone, no group header in AccountCode)
        seed(AccountCode.SHARE_CAPITAL,       "Share Capital",                     AccountType.Equity,    null);
        seed(AccountCode.RETAINED_EARNINGS,   "Retained Earnings",                 AccountType.Equity,    null);
        seed(AccountCode.CURRENT_YEAR_PNL,    "Current Year Profit / Loss",        AccountType.Equity,    null);

        // ── Assets (parent = CURRENT_ASSETS) ────────────────────────────────
        ChartOfAccount assetParent = repo.findByCode(AccountCode.CURRENT_ASSETS).orElse(null);
        seed(AccountCode.CASH,           "Cash in Hand",                  AccountType.Asset, assetParent);
        seed(AccountCode.BANK_KBZ,       "KBZ Bank Account",              AccountType.Asset, assetParent);
        seed(AccountCode.RECEIVABLE,     "Accounts Receivable",           AccountType.Asset, assetParent);
        seed(AccountCode.INVENTORY,      "Inventory / Stock",             AccountType.Asset, assetParent);
        seed(AccountCode.KPAY,           "KPay",                          AccountType.Asset, assetParent);
        seed(AccountCode.WAVE_PAY,       "Wave Pay",                      AccountType.Asset, assetParent);
        seed(AccountCode.FIXED_ASSETS,   "Fixed Assets & Equipment",      AccountType.Asset, assetParent);

        // ── Liabilities (parent = CURRENT_LIABILITIES) ──────────────────────
        ChartOfAccount liabParent = repo.findByCode(AccountCode.CURRENT_LIABILITIES).orElse(null);
        seed(AccountCode.PAYABLE,           "Accounts Payable",   AccountType.Liability, liabParent);
        seed(AccountCode.SALARY_PAYABLE,    "Salary Payable",     AccountType.Liability, liabParent);
        seed(AccountCode.CUSTOMER_ADVANCE,  "Customer Advance",   AccountType.Liability, liabParent);

        // ── Income (parent = OPERATING_INCOME) ──────────────────────────────
        ChartOfAccount incomeParent = repo.findByCode(AccountCode.OPERATING_INCOME).orElse(null);
        seed(AccountCode.SALES,             "Product Sales",                      AccountType.Income, incomeParent);
        seed(AccountCode.CONSULTING_REVENUE,"Consulting Revenue",                 AccountType.Income, incomeParent);
        seed(AccountCode.SERVICE_REVENUE,   "Service Revenue",                    AccountType.Income, incomeParent);
        seed(AccountCode.COMMISSION,        "Commission",                         AccountType.Income, incomeParent);
        seed(AccountCode.INVENTORY_GAIN,    "Inventory Gain",                     AccountType.Income, incomeParent);
        seed(AccountCode.PURCHASE_RTN,      "Purchase Returns & Allowances",      AccountType.Income, incomeParent);
        seed(AccountCode.INVENTORY_OVER,    "Inventory Over",                     AccountType.Income, incomeParent);
        seed(AccountCode.OTHER_INCOME,      "Other Income",                       AccountType.Income, incomeParent);

        // ── Expenses (parent = OPERATING_EXPENSES) ──────────────────────────
        ChartOfAccount expenseParent = repo.findByCode(AccountCode.OPERATING_EXPENSES).orElse(null);
        seed(AccountCode.COGS,            "Cost of Goods Sold",             AccountType.Expense, expenseParent);
        seed(AccountCode.PURCHASES,       "Purchases",                      AccountType.Expense, expenseParent);
        seed(AccountCode.OPENING_STOCK,   "Opening Stock",                  AccountType.Expense, expenseParent);
        seed(AccountCode.SALES_RTN,       "Sales Returns & Allowances",     AccountType.Expense, expenseParent);
        seed(AccountCode.INVENTORY_LOSS,  "Inventory Loss",                 AccountType.Expense, expenseParent);
        seed(AccountCode.INVENTORY_SHORT, "Inventory Short",                AccountType.Expense, expenseParent);
        seed(AccountCode.SALARY,          "Salary Expense",                 AccountType.Expense, expenseParent);
        seed(AccountCode.OFFICE_RENT,     "Office Rent",                    AccountType.Expense, expenseParent);
        seed(AccountCode.ELECTRICITY,     "Electricity Bill",               AccountType.Expense, expenseParent);
        seed(AccountCode.INTERNET,        "Internet Charges",               AccountType.Expense, expenseParent);
        seed(AccountCode.PHONE_BILL,      "Phone Bill",                     AccountType.Expense, expenseParent);
        seed(AccountCode.TRANSPORTATION,  "Transportation",                 AccountType.Expense, expenseParent);
        seed(AccountCode.MAINTENANCE,     "Maintenance",                    AccountType.Expense, expenseParent);
        seed(AccountCode.STATIONERY,      "Stationery",                     AccountType.Expense, expenseParent);
        seed(AccountCode.MARKETING,       "Marketing",                      AccountType.Expense, expenseParent);
        seed(AccountCode.PETROL_FUEL,     "Petrol & Fuel",                  AccountType.Expense, expenseParent);
        seed(AccountCode.SERVICE_REPAIR,  "Service & Repair Expense",       AccountType.Expense, expenseParent);
        seed(AccountCode.LOAN_INTEREST,   "Loan Interest Expense",          AccountType.Expense, expenseParent);
        seed(AccountCode.TAX_EXPENSE,     "Tax Expense",                    AccountType.Expense, expenseParent);
        seed(AccountCode.DECORATION,      "Decoration & Renovation",        AccountType.Expense, expenseParent);
        seed(AccountCode.STAFF_ALLOWANCE, "Staff Allowance",                AccountType.Expense, expenseParent);
        seed(AccountCode.DONATION,        "Donation",                       AccountType.Expense, expenseParent);
        seed(AccountCode.GENERAL_SALE_EXP,"General Sale Expense",           AccountType.Expense, expenseParent);
        seed(AccountCode.MISC_EXPENSE,    "Miscellaneous Expense",          AccountType.Expense, expenseParent);

        log.info("COA seeding completed");
    }

    private void seed(String code, String name, AccountType type, ChartOfAccount parent) {
        if (repo.existsByCode(code)) return;
        ChartOfAccount coa = new ChartOfAccount();
        coa.setCode(code);
        coa.setAccountName(name);
        coa.setAccountType(type);
        coa.setParent(parent);
        repo.save(coa);
    }
}
