package org.sspd.servicemgmt.accountingoptions.coaoptions;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.sspd.servicemgmt.accountingoptions.coaoptions.model.ChartOfAccount;
import org.sspd.servicemgmt.accountingoptions.coaoptions.repository.ChartOfAccountRepository;

@Component
@RequiredArgsConstructor
public class AccountResolver {

    private final ChartOfAccountRepository coaRepo;

    public ChartOfAccount get(String code) {
        return coaRepo.findByCode(code)
                .orElseThrow(() -> new IllegalStateException("Account not found: " + code));
    }

    // ── Assets ──────────────────────────────────────
    public ChartOfAccount cash()            { return get(AccountCode.CASH); }
    public ChartOfAccount bankKbz()         { return get(AccountCode.BANK_KBZ); }
    public ChartOfAccount receivable()      { return get(AccountCode.RECEIVABLE); }
    public ChartOfAccount inventory()       { return get(AccountCode.INVENTORY); }
    public ChartOfAccount kpay()            { return get(AccountCode.KPAY); }
    public ChartOfAccount wavePay()         { return get(AccountCode.WAVE_PAY); }

    // ── Liabilities ─────────────────────────────────
    public ChartOfAccount payable()         { return get(AccountCode.PAYABLE); }
    public ChartOfAccount salaryPayable()   { return get(AccountCode.SALARY_PAYABLE); }
    public ChartOfAccount custAdvance()     { return get(AccountCode.CUSTOMER_ADVANCE); }

    // ── Income ──────────────────────────────────────
    public ChartOfAccount sales()           { return get(AccountCode.SALES); }
    public ChartOfAccount consulting()      { return get(AccountCode.CONSULTING_REVENUE); }
    public ChartOfAccount purchaseRtn()     { return get(AccountCode.PURCHASE_RTN); }
    public ChartOfAccount inventoryGain()   { return get(AccountCode.INVENTORY_GAIN); }
    public ChartOfAccount inventoryOver()   { return get(AccountCode.INVENTORY_OVER); }
    public ChartOfAccount serviceRevenue() { return get(AccountCode.SERVICE_REVENUE); }
    public ChartOfAccount commission()     { return get(AccountCode.COMMISSION); }
    public ChartOfAccount otherIncome()    { return get(AccountCode.OTHER_INCOME); }

    // ── Expenses ────────────────────────────────────
    public ChartOfAccount openingStock()    { return get(AccountCode.OPENING_STOCK); }
    public ChartOfAccount purchases()       { return get(AccountCode.PURCHASES); }
    public ChartOfAccount cogs()            { return get(AccountCode.COGS); }
    public ChartOfAccount salesRtn()        { return get(AccountCode.SALES_RTN); }
    public ChartOfAccount inventoryLoss()   { return get(AccountCode.INVENTORY_LOSS); }
    public ChartOfAccount inventoryShort()  { return get(AccountCode.INVENTORY_SHORT); }
    public ChartOfAccount officeRent()      { return get(AccountCode.OFFICE_RENT); }
    public ChartOfAccount electricity()     { return get(AccountCode.ELECTRICITY); }
    public ChartOfAccount internet()        { return get(AccountCode.INTERNET); }
    public ChartOfAccount salary()         { return get(AccountCode.SALARY); }
    public ChartOfAccount transportation() { return get(AccountCode.TRANSPORTATION); }
    public ChartOfAccount maintenance()    { return get(AccountCode.MAINTENANCE); }
    public ChartOfAccount stationery()     { return get(AccountCode.STATIONERY); }
    public ChartOfAccount marketing()      { return get(AccountCode.MARKETING); }
    public ChartOfAccount miscExpense()    { return get(AccountCode.MISC_EXPENSE); }
    public ChartOfAccount serviceRepair()  { return get(AccountCode.SERVICE_REPAIR); }
    public ChartOfAccount loanInterest()   { return get(AccountCode.LOAN_INTEREST); }
    public ChartOfAccount phoneBill()      { return get(AccountCode.PHONE_BILL); }
    public ChartOfAccount taxExpense()     { return get(AccountCode.TAX_EXPENSE); }
    public ChartOfAccount petrolFuel()     { return get(AccountCode.PETROL_FUEL); }
    public ChartOfAccount decoration()     { return get(AccountCode.DECORATION); }
    public ChartOfAccount staffAllowance() { return get(AccountCode.STAFF_ALLOWANCE); }
    public ChartOfAccount donation()       { return get(AccountCode.DONATION); }
    public ChartOfAccount generalSaleExp() { return get(AccountCode.GENERAL_SALE_EXP); }

    // ── Fixed Assets ────────────────────────────────
    public ChartOfAccount fixedAssets()    { return get(AccountCode.FIXED_ASSETS); }
}
