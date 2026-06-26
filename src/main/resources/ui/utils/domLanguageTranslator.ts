import { AppLanguage } from '../types';

const NON_TRANSLATABLE_TAGS = new Set(['SCRIPT', 'STYLE', 'NOSCRIPT', 'CODE', 'PRE']);
const TRANSLATABLE_ATTRS = ['placeholder', 'title', 'aria-label', 'alt'] as const;
const BUTTON_INPUT_TYPES = new Set(['button', 'submit', 'reset']);

const enToMy: Record<string, string> = {
  
  'General': 'အထွေထွေ',
  'Human Resources': 'လူ့စွမ်းအားအရင်းအမြစ်',
  'Inventory': 'ကုန်လှောင်နှင့်ပစ္စည်း',
  'Procurement': 'ဝယ်ယူရေး',
  'CRM': 'ဖောက်သည်စီမံခန့်ခွဲမှု',
  'Accounting': 'စာရင်းကိုင်',
  'Security': 'လုံခြုံရေး',
  'Analytics': 'သုံးသပ်ချက်',
  'Staff Members': 'ဝန်ထမ်းများ',
  'Products': 'ကုန်ပစ္စည်းများ',
  'Serials': 'စီရီးယယ်များ',
  'Brands': 'အမှတ်တံဆိပ်များ',
  'Categories': 'အမျိုးအစားများ',
  'Units': 'ယူနစ်များ',
  'Purchases': 'ဝယ်ယူမှုများ',
  'Purchase Returns': 'ဝယ်ယူမှုပြန်အပ်မှု',
  'Suppliers': 'ပေးသွင်းသူများ',
  'Sales': 'အရောင်းများ',
  'Customers': 'ဖောက်သည်များ',
  'Accounting Dashboard': 'စာရင်းကိုင် ဒက်ရှ်ဘုတ်',
  'Chart of Accounts': 'အကောင့်ဇယား',
  'Journal Entries': 'ဂျာနယ် Entries',
  'Payment Methods': 'ငွေပေးချေမှုနည်းလမ်းများ',
  'User Access': 'အသုံးပြုသူအခွင့်အရေး',
  'Roles': 'ရာထူးများ',
  'Permissions': 'ခွင့်ပြုချက်များ',
  'Sign Out': 'ထွက်မည်',
  'Secure Access Granted': 'လုံခြုံဝင်ခွင့် ရရှိပါသည်',
  'Welcome back': 'ပြန်လည်ကြိုဆိုပါသည်',
  'Access Denied': 'ဝင်ခွင့်မရှိပါ',
  'Username or Email': 'အသုံးပြုသူအမည် သို့မဟုတ် အီးမေးလ်',
  'Password': 'စကားဝှက်',
  'Enter your account': 'သင့်အကောင့်ထည့်ပါ',
  'Hide password': 'စကားဝှက်ဖျောက်ရန်',
  'Show password': 'စကားဝှက်ပြရန်',
  'Sign In to Dashboard': 'ဒက်ရှ်ဘုတ်သို့ ဝင်မည်',
  'Authenticating...': 'စစ်ဆေးနေသည်...',
  'Secure Access': 'လုံခြုံဝင်ခွင့်',
  
  
  'Create Sale Voucher': 'အရောင်းဘောင်ချာအသစ်ဖန်တီးမည်',
  'New Sale': 'အရောင်းအသစ်',
  'Save Sale': 'အရောင်းသိမ်းမည်',
  'Save Customer': 'ဖောက်သည်သိမ်းမည်',
  'Saving...': 'သိမ်းဆည်းနေသည်...',
  'Cancel': 'မလုပ်တော့ပါ',
  'Confirm': 'အတည်ပြုမည်',
  'Discard': 'ပယ်ဖျက်မည်',
  'Apply': 'အသုံးချမည်',
  'Refresh': 'ပြန်တင်မည်',
  'Search': 'ရှာဖွေမည်',
  'Search by voucher, customer, staff...': 'ဘောင်ချာ၊ ဖောက်သည်၊ ဝန်ထမ်း အလိုက်ရှာဖွေပါ...',
  'Search customer by name / phone': 'ဖောက်သည်အမည် / ဖုန်းဖြင့် ရှာပါ',
  'Search product by name / code': 'ကုန်ပစ္စည်းအမည် / code ဖြင့် ရှာပါ',
  'No customer found.': 'ဖောက်သည်မတွေ့ပါ',
  'No product found.': 'ကုန်ပစ္စည်းမတွေ့ပါ',
  'No records found': 'မှတ်တမ်းမတွေ့ပါ',
  'NO RECORDS FOUND': 'မှတ်တမ်းမတွေ့ပါ',
  'Loading...': 'တင်နေသည်...',
  'Create and track product sales with serial numbers.': 'စီရီးယယ်နံပါတ်များဖြင့် အရောင်းဖန်တီးပြီး စောင့်ကြည့်ပါ။',
  'Use exact serials and finalize paid/credit amount before saving.': 'သိမ်းမယ့်အချိန်မတိုင်ခင် serial နံပါတ်နှင့် ပေးချေ/အကြွေးပမာဏကို အတည်ပြုပါ။',
  'Customer': 'ဖောက်သည်',
  'Staff': 'ဝန်ထမ်း',
  'Payment Method': 'ငွေပေးချေမှုနည်းလမ်း',
  'Payment method': 'ငွေပေးချေမှုနည်းလမ်း',
  'Remark': 'မှတ်ချက်',
  'Product': 'ကုန်ပစ္စည်း',
  'Qty': 'အရေအတွက်',
  'Unit Price': 'တစ်ယူနစ်စျေး',
  'Unit Cost': 'တစ်ယူနစ်ကုန်ကျစရိတ်',
  'Subtotal': 'စုစုပေါင်းခွဲ',
  'Status': 'အခြေအနေ',
  'Action': 'လုပ်ဆောင်ချက်',
  'Actions': 'လုပ်ဆောင်ချက်များ',
  'Date': 'ရက်စွဲ',
  'Amount': 'ပမာဏ',
  'Transaction No': 'ငွေလွှဲအမှတ်',
  'Transaction No (optional)': 'ငွေလွှဲအမှတ် (optional)',
  'Total': 'စုစုပေါင်း',
  'Net': 'နက်',
  'Paid': 'ပေးပြီး',
  'Due': 'ကျန်ငွေ',
  'Total Amount': 'စုစုပေါင်းပမာဏ',
  'Total Net': 'စုစုပေါင်းနက်',
  'Total Paid': 'စုစုပေါင်းပေးပြီး',
  'Outstanding (Due)': 'ကျန်ငွေ',
  'Cash Balance': 'ငွေသားလက်ကျန်',
  'KBZ Balance': 'KBZ လက်ကျန်',
  'Total Vouchers': 'ဘောင်ချာစုစုပေါင်း',
  'Today Sales': 'ယနေ့အရောင်း',
  'Sale Management': 'အရောင်းစီမံခန့်ခွဲမှု',
  'Sales Ledger': 'အရောင်းစာရင်း',
  'Sale No': 'အရောင်းနံပါတ်',
  'Voucher': 'ဘောင်ချာ',
  'Items': 'ပစ္စည်းအရေအတွက်',
  'Add Row': 'တန်းထည့်မည်',
  'Add Unit': 'ယူနစ်ထည့်မည်',
  'Add': 'ထည့်မည်',
  'Save': 'သိမ်းမည်',
  'Delete': 'ဖျက်မည်',
  'Edit': 'ပြင်မည်',
  'Select customer': 'ဖောက်သည်ရွေးပါ',
  'Select staff': 'ဝန်ထမ်းရွေးပါ',
  'Select payment method': 'ငွေပေးချေမှုနည်းလမ်းရွေးပါ',
  'Select Payment Method': 'ငွေပေးချေမှုနည်းလမ်းရွေးပါ',
  'Select product': 'ကုန်ပစ္စည်းရွေးပါ',
  'Select account': 'အကောင့်ရွေးပါ',
  'Select method': 'နည်းလမ်းရွေးပါ',
  'Select Associated Product': 'ဆက်စပ်ကုန်ပစ္စည်းရွေးပါ',
  'All': 'အားလုံး',
  'All Status': 'အခြေအနေအားလုံး',
  'Partial': 'တစ်စိတ်တစ်ပိုင်း',
  'Credit': 'အကြွေး',
  'Credit Sale': 'အကြွေးအရောင်း',
  'Fully Paid': 'အပြည့်ပေးပြီး',
  'Partial Payment': 'တစ်စိတ်တစ်ပိုင်းပေးချေမှု',
  'No sales found for current filter.': 'လက်ရှိ filter အတွက် အရောင်းမတွေ့ပါ',
  'No purchases match the current filters.': 'လက်ရှိ filter နှင့် ကိုက်ညီသော ဝယ်ယူမှုမရှိပါ',
  'Purchase ledger': 'ဝယ်ယူမှုစာရင်း',
  'New Purchase Voucher': 'ဝယ်ယူမှုဘောင်ချာအသစ်',
  'Supplier': 'ပေးသွင်းသူ',
  'Buyer': 'ဝယ်ယူသူ',
  'Record Payment': 'ငွေပေးချေမှုမှတ်တမ်း',
  'Record Sale Payment': 'အရောင်းငွေပေးချေမှု မှတ်တမ်း',
  'Record': 'မှတ်တမ်း',
  'Serial Numbers': 'စီရီးယယ်နံပါတ်များ',
  'Auto Fill': 'အလိုအလျောက်ဖြည့်',
  'Available SN': 'ရနိုင်သော SN',
  'Code:': 'ကုဒ်:',
  'Base:': 'အခြေခံ:',
  'Full': 'အပြည့်',
  'No transactions found': 'ငွေလွှဲမှတ်တမ်းမတွေ့ပါ',
  'Account': 'အကောင့်',
  'Account Name': 'အကောင့်အမည်',
  'Fiscal Year': 'ဘဏ္ဍာရေးနှစ်',
  'Current Balance': 'လက်ရှိလက်ကျန်',
  'Opening Balance': 'ဖွင့်လှစ်လက်ကျန်',
  'Last Updated': 'နောက်ဆုံးအပ်ဒိတ်',
  'Account Balances': 'အကောင့်လက်ကျန်များ',
  'Account Balances (Trial Balance)': 'အကောင့်လက်ကျန် (Trial Balance)',
  'Payment Transactions': 'ငွေပေးချေမှုလုပ်ငန်းများ',
  'Set Opening Balance': 'ဖွင့်လှစ်လက်ကျန် သတ်မှတ်မည်',
  'Opening Balances List': 'ဖွင့်လှစ်လက်ကျန်စာရင်း',
  'No balance records found': 'လက်ကျန်မှတ်တမ်းမတွေ့ပါ',
  'Customer Registry': 'ဖောက်သည်မှတ်ပုံတင်',
  'Identity Profile': 'အချက်အလက်ပရိုဖိုင်',
  'Communication': 'ဆက်သွယ်ရေး',
  'Physical Address': 'လိပ်စာ',
  'Action Hub': 'လုပ်ဆောင်ချက်',
  'Brand Registry': 'အမှတ်တံဆိပ်မှတ်ပုံတင်',
  'Brand Name': 'အမှတ်တံဆိပ်အမည်',
  'Serial Registry': 'စီရီးယယ်မှတ်ပုံတင်',
  'Measurement Units': 'တိုင်းတာယူနစ်များ',
  'Identity Directory': 'အသုံးပြုသူစာရင်း',
  'Assign Roles': 'Role သတ်မှတ်မည်',
  'Username': 'အသုံးပြုသူအမည်',
  'Email': 'အီးမေးလ်',
  'Role': 'တာဝန်အဆင့်',
  'Verified Accounts': 'အတည်ပြုအကောင့်များ',
  'Master List': 'စာရင်းအပြည့်',
  'Showing': 'ဖော်ပြနေသည်',
  'Showing:': 'ဖော်ပြနေသည်:',
  'per page': 'တစ်မျက်နှာလျှင်',
  'No Customers Found': 'ဖောက်သည်မတွေ့ပါ',
  'No records found for current filter.': 'လက်ရှိ filter အတွက် မှတ်တမ်းမတွေ့ပါ',
  'Payment Date': 'ငွေပေးချေသည့်ရက်',
  'Type': 'အမျိုးအစား',
  'Method': 'နည်းလမ်း',
  'Supplier Name': 'ပေးသွင်းသူအမည်',
  'No returns found for this purchase.': 'ဤဝယ်ယူမှုအတွက် ပြန်အပ်မှုမတွေ့ပါ',
  'Loading related returns...': 'ဆက်စပ်ပြန်အပ်မှုများ တင်နေသည်...',
  'Reason': 'အကြောင်းပြချက်',
  'Return No': 'ပြန်အပ်နံပါတ်',
  'Credit Management': 'အကြွေးစီမံခန့်ခွဲမှု',
  'Manage credit terms, alerts and customer payment settlements.': 'အကြွေးစည်းကမ်း၊ အချက်ပေးသတိပေးချက်နှင့် ဖောက်သည်ပေးချေမှုများကို စီမံပါ။',
  'Active Credit Terms': 'အသုံးပြုနေသော အကြွေးစည်းကမ်းများ',
  'Overdue Alerts': 'Due လွန်သတိပေးချက်များ',
  'Total Outstanding': 'ကျန်ငွေစုစုပေါင်း',
  'Customer Filter': 'ဖောက်သည် filter',
  'All customers': 'ဖောက်သည်အားလုံး',
  'New Credit Term': 'အကြွေးစည်းကမ်းအသစ်',
  'Credit Terms': 'အကြွေးစည်းကမ်းများ',
  'Limit': 'ကန့်သတ်ငွေ',
  'Days': 'ရက်',
  'Allowed': 'ခွင့်ပြု',
  'Yes': 'ဟုတ်',
  'No': 'မဟုတ်',
  'No credit terms found.': 'အကြွေးစည်းကမ်းမတွေ့ပါ',
  'Credit Alerts': 'အကြွေးသတိပေးချက်များ',
  'Alert Date': 'သတိပေးရက်',
  'Resolve': 'ဖြေရှင်းမည်',
  'No unresolved alerts.': 'မဖြေရှင်းရသေးသော alert မရှိပါ',
  'Customer Payments': 'ဖောက်သည်ပေးချေမှုများ',
  'Sale (optional)': 'အရောင်း (optional)',
  'Advance payment': 'ကြိုတင်ပေးချေမှု',
  'Transaction': 'Transaction',
  'Select customer to view payments.': 'ပေးချေမှုကြည့်ရန် ဖောက်သည်ရွေးပါ',
  'Edit Credit Term': 'အကြွေးစည်းကမ်းပြင်မည်',
  'Credit Limit': 'အကြွေးကန့်သတ်ငွေ',
  'Credit Days': 'အကြွေးရက်',
  'Credit allowed for this customer': 'ဤဖောက်သည်အတွက် အကြွေးခွင့်ပြုမည်',
  'Save Term': 'စည်းကမ်းသိမ်းမည်',
  'Credit term saved': 'အကြွေးစည်းကမ်းသိမ်းပြီး',
  'Alert resolved': 'သတိပေးချက်ဖြေရှင်းပြီး',

  'Dashboard': 'ဒက်ရှ်ဘုတ်',
  'Management System': 'စီမံခန့်ခွဲမှုစနစ်',
  'Expand sidebar': 'ဘေးမီနူးချဲ့မည်',
  'Collapse sidebar': 'ဘေးမီနူးချုံ့မည်',
  'Switch to light mode': 'အလင်းမုဒ်သို့ပြောင်းမည်',
  'Switch to dark mode': 'အမှောင်မုဒ်သို့ပြောင်းမည်',
  'Switch to English': 'အင်္ဂလိပ်ဘာသာသို့ပြောင်းမည်',
  'Switch to Myanmar': 'မြန်မာဘာသာသို့ပြောင်းမည်',
  'Keyboard shortcuts': 'ကီးဘုတ်ဖြတ်လမ်းများ',
  'Keyboard shortcuts (?)': 'ကီးဘုတ်ဖြတ်လမ်းများ (?)',
  'Light': 'အလင်း',
  'Dark': 'အမှောင်',
  'User': 'အသုံးပြုသူ',
  'Admin': 'အုပ်ချုပ်သူ',
  'Administrator': 'အုပ်ချုပ်သူ',
  'Required': 'လိုအပ်သည်',
  'Optional': 'မဖြစ်မနေမဟုတ်',
  'Back': 'နောက်သို့',
  'Next': 'ရှေ့သို့',
  'Finish Setup': 'စတင်ပြင်ဆင်မှု ပြီးဆုံးမည်',
  'Go to Dashboard': 'ဒက်ရှ်ဘုတ်သို့ သွားမည်',
  'Welcome to SSPD': 'SSPD မှ ကြိုဆိုပါသည်',
  'Set up your business in 2 quick steps': 'သင့်လုပ်ငန်းကို အဆင့် ၂ ဆင့်ဖြင့် ပြင်ဆင်ပါ',
  'Company Info': 'ကုမ္ပဏီအချက်အလက်',
  'Company Information': 'ကုမ္ပဏီအချက်အလက်',
  'Company Name': 'ကုမ္ပဏီအမည်',
  'Company Settings': 'ကုမ္ပဏီဆက်တင်',
  'This will appear on all invoices and receipts.': 'ဤအချက်အလက်များသည် invoice နှင့် receipt အားလုံးတွင် ဖော်ပြပါမည်။',
  'e.g. SSPD IT Solution Center': 'ဥပမာ SSPD IT Solution Center',
  'Street, Township, City': 'လမ်း၊ မြို့နယ်၊ မြို့',
  'Set up your business': 'လုပ်ငန်းပြင်ဆင်မည်',
  'Select how your customers pay. These link to your accounting accounts automatically.': 'ဖောက်သည်များပေးချေမည့်နည်းလမ်းများကိုရွေးပါ။ စာရင်းကိုင်အကောင့်များနှင့် အလိုအလျောက်ချိတ်ဆက်ပါမည်။',
  'You can always add more payment methods later in Settings.': 'နောက်ပိုင်း Settings တွင် ပေးချေနည်းလမ်းများ ထပ်ထည့်နိုင်ပါသည်။',
  'Setup Complete!': 'စတင်ပြင်ဆင်မှု ပြီးပါပြီ',
  'Your business is ready. Payment methods have been linked to your accounting accounts automatically.': 'သင့်လုပ်ငန်း အသင့်ဖြစ်ပါပြီ။ ပေးချေနည်းလမ်းများကို စာရင်းကိုင်အကောင့်များနှင့် အလိုအလျောက်ချိတ်ဆက်ပြီးပါပြီ။',
  'Setup failed. Please try again.': 'စတင်ပြင်ဆင်မှု မအောင်မြင်ပါ။ ပြန်ကြိုးစားပါ။',
  'You can change these settings anytime in Company Settings.': 'ဤဆက်တင်များကို Company Settings တွင် အချိန်မရွေးပြောင်းနိုင်ပါသည်။',
  'Company:': 'ကုမ္ပဏီ:',
  'Payment Methods:': 'ပေးချေနည်းလမ်းများ:',
  'COA accounts: Auto-linked': 'COA အကောင့်များ: အလိုအလျောက်ချိတ်ဆက်ပြီး',
  'Ready to create sales & reports': 'အရောင်းနှင့်အစီရင်ခံစာများ ပြုလုပ်ရန် အသင့်',
  'Cash': 'ငွေသား',
  'KBZ Bank': 'KBZ ဘဏ်',
  'KPay': 'KPay',
  'Wave Pay': 'Wave Pay',
  'Business Overview': 'လုပ်ငန်းခြုံငုံသုံးသပ်ချက်',
  'Quick Actions': 'အမြန်လုပ်ဆောင်ချက်များ',
  'Navigation': 'လမ်းညွှန်',
  'Business Summary': 'လုပ်ငန်းအကျဉ်းချုပ်',
  'Recent Sales': 'မကြာသေးမီ အရောင်းများ',
  'Recent sale': 'မကြာသေးမီ အရောင်း',
  'Full Ledger': 'မှတ်တမ်းအပြည့်',
  'No sales yet': 'အရောင်းမရှိသေးပါ',
  'create your first sale': 'ပထမဆုံးအရောင်းပြုလုပ်ပါ',
  'Create your first sale': 'ပထမဆုံးအရောင်းပြုလုပ်ပါ',
  'Accounting data not initialized': 'စာရင်းကိုင်ဒေတာ မစတင်ရသေးပါ',
  'Journal entries missing': 'ဂျာနယ်မှတ်တမ်းများ မရှိသေးပါ',
  'existing sales/purchases are not reflected in financial reports. Click to backfill from current data.': 'ရှိပြီးသား အရောင်း/အဝယ်များသည် ငွေရေးကြေးရေးအစီရင်ခံစာများတွင် မပါသေးပါ။ လက်ရှိဒေတာမှပြန်ဖြည့်ရန် နှိပ်ပါ။',
  'Backfilling...': 'ပြန်ဖြည့်နေသည်...',
  'Backfill Now': 'ယခုပြန်ဖြည့်မည်',
  'Backfill complete': 'ပြန်ဖြည့်ပြီးပါပြီ',
  'Journal entries created from existing transactions. Financial reports (Trial Balance, P&L, Balance Sheet) should now show data.': 'ရှိပြီးသား transaction များမှ ဂျာနယ်မှတ်တမ်းများ ဖန်တီးပြီးပါပြီ။ ငွေရေးကြေးရေးအစီရင်ခံစာများတွင် ဒေတာများ ပြသပါမည်။',
  'View Credits': 'အကြွေးများကြည့်မည်',
  'View Products': 'ကုန်ပစ္စည်းများကြည့်မည်',
  'overdue invoice': 'သတ်မှတ်ရက်လွန် invoice',
  'overdue invoices': 'သတ်မှတ်ရက်လွန် invoice များ',
  'total': 'စုစုပေါင်း',
  'These customers have passed their due dates. Follow up immediately to collect.': 'ဤဖောက်သည်များသည် ပေးချေရမည့်ရက် ကျော်လွန်နေပါသည်။ ငွေကောက်ခံရန် ချက်ချင်းဆက်သွယ်ပါ။',
  'product running low on stock': 'ကုန်ပစ္စည်း လက်ကျန်နည်းနေသည်',
  'products running low on stock': 'ကုန်ပစ္စည်းများ လက်ကျန်နည်းနေသည်',
  'Low items:': 'လက်ကျန်နည်းသောပစ္စည်းများ:',
  'more': 'ခု ထပ်ရှိသည်',
  'Stock at or below 5 units.': 'လက်ကျန် ၅ ခု သို့မဟုတ် ၅ ခုအောက်ဖြစ်နေသည်။',
  'Overdue': 'သတ်မှတ်ရက်လွန်',
  'Pending': 'စောင့်ဆိုင်းနေ',
  'Complete': 'ပြီးဆုံး',
  'Completed': 'ပြီးဆုံးပြီး',
  'Done': 'ပြီးဆုံး',
  'Processing': 'ဆောင်ရွက်နေ',
  'In Progress': 'ဆောင်ရွက်နေ',
  'Received': 'လက်ခံပြီး',
  'Rejected': 'ငြင်းပယ်ပြီး',
  'Cancelled': 'ပယ်ဖျက်ပြီး',
  'Unpaid': 'မပေးချေရသေး',
  'Draft': 'မူကြမ်း',
  'Approved': 'အတည်ပြုပြီး',
  'Deleted Successfully': 'အောင်မြင်စွာ ဖျက်ပြီးပါပြီ',
  'Delete?': 'ဖျက်မလား',
  'Yes, Delete': 'ဟုတ်ကဲ့၊ ဖျက်မည်',
  'Validation': 'အချက်အလက်စစ်ဆေးမှု',
  'Success': 'အောင်မြင်သည်',
  'Failed': 'မအောင်မြင်ပါ',
  'Error': 'အမှား',
  'Warning': 'သတိပေးချက်',
  'Info': 'အချက်အလက်',
  'Restricted': 'ကန့်သတ်ထားသည်',
  'Enabled': 'ဖွင့်ထားသည်',
  'Disabled': 'ပိတ်ထားသည်',
  'Online': 'အွန်လိုင်း',
  'Offline': 'အော့ဖ်လိုင်း',
  'Open': 'ဖွင့်မည်',
  'Close': 'ပိတ်မည်',
  'View': 'ကြည့်မည်',
  'Details': 'အသေးစိတ်',
  'Create': 'ဖန်တီးမည်',
  'Update': 'ပြင်ဆင်မည်',
  'Submit': 'တင်သွင်းမည်',
  'Clear': 'ရှင်းမည်',
  'Reset': 'ပြန်သတ်မှတ်မည်',
  'Download': 'ဒေါင်းလုဒ်',
  'Upload': 'အပ်လုဒ်',
  'Export': 'ထုတ်ယူမည်',
  'Import': 'ထည့်သွင်းမည်',
  'Print': 'ပရင့်ထုတ်မည်',
  'Preview': 'အစမ်းကြည့်မည်',
  'Copy': 'ကူးမည်',
  'Load': 'ဖွင့်ယူမည်',
  'OK': 'အိုကေ',
  'Name': 'အမည်',
  'Phone': 'ဖုန်း',
  'Address': 'လိပ်စာ',
  'Price': 'စျေးနှုန်း',
  'Quantity': 'အရေအတွက်',
  'Description': 'ဖော်ပြချက်',
  'Category': 'အမျိုးအစား',
  'Brand': 'အမှတ်တံဆိပ်',
  'Model': 'မော်ဒယ်',
  'Serial': 'စီရီယယ်',
  'Serial No': 'စီရီယယ်နံပါတ်',
  'Code': 'ကုဒ်',
  'Filter': 'စစ်ထုတ်မည်',
  'From': 'မှ',
  'To': 'ထိ',
  'Start Date': 'စတင်ရက်',
  'End Date': 'ပြီးဆုံးရက်',
  'Search...': 'ရှာဖွေပါ...',
  'Select': 'ရွေးပါ',
  'Choose': 'ရွေးချယ်ပါ',
  'No data': 'ဒေတာမရှိပါ',
  'No data found': 'ဒေတာမတွေ့ပါ',
  'No items': 'ပစ္စည်းမရှိပါ',
  'No accounts found': 'အကောင့်မတွေ့ပါ',
  'No presets saved.': 'သိမ်းထားသော preset မရှိပါ',
  'Prepared By': 'ပြင်ဆင်သူ',
  'Received By': 'လက်ခံသူ',
  'Staff Signature': 'ဝန်ထမ်းလက်မှတ်',
  'Customer Signature': 'ဖောက်သည်လက်မှတ်',
  'Customer Name': 'ဖောက်သည်အမည်',
  'Customer Phone': 'ဖောက်သည်ဖုန်း',
  'Sale Info': 'အရောင်းအချက်အလက်',
  'Item': 'ပစ္စည်း',
  'Net Amount': 'ကျသင့်ငွေ',
  'Amount (Ks)': 'ပမာဏ (ကျပ်)',
  'Expense': 'ကုန်ကျစရိတ်',
  'Income': 'ဝင်ငွေ',
  'Add Expense': 'ကုန်ကျစရိတ်ထည့်မည်',
  'New Expense': 'ကုန်ကျစရိတ်အသစ်',
  'Save Expense': 'ကုန်ကျစရိတ်သိမ်းမည်',
  'Expense details...': 'ကုန်ကျစရိတ်အသေးစိတ်...',
  'Select account...': 'အကောင့်ရွေးပါ...',
  'Select method (optional)...': 'နည်းလမ်းရွေးပါ (မဖြစ်မနေမဟုတ်)...',
  'Enter a valid amount': 'မှန်ကန်သောပမာဏထည့်ပါ',
  'Could not save expense': 'ကုန်ကျစရိတ် မသိမ်းနိုင်ပါ',
  'Could not connect to server': 'ဆာဗာသို့ ချိတ်ဆက်မရပါ',
  'Total Sales': 'စုစုပေါင်းအရောင်း',
  'Total Service': 'စုစုပေါင်းဝန်ဆောင်မှု',
  'Service Jobs': 'ဝန်ဆောင်မှုလုပ်ငန်းများ',
  'No activity this month': 'ဤလအတွင်း လုပ်ဆောင်ချက်မရှိပါ',
  'No data for this month.': 'ဤလအတွက် ဒေတာမရှိပါ။',

  'Profit & Loss Report': 'အမြတ်အစွန်းနှင့် အရှုံးအစားရင်း',
  'Profit & Loss': 'အမြတ်အစွန်း / အရှုံးအစား',
  'Profit & Loss Statement': 'အမြတ်အစွန်း / အရှုံးအစား ထုတ်ပြန်ချက်',
  'Revenue, COGS, Expenses & Net Profit': 'ဝင်ငွေ၊ ကုန်ကျစရိတ်နှင့် အသားတင်အမြတ်',
  'Revenue': 'ဝင်ငွေ',
  'Total net sales amount': 'အသားတင် အရောင်းငွေ စုစုပေါင်း',
  'Cost of Goods Sold (COGS)': 'ရောင်းသောကုန်ပစ္စည်း ကုန်ကျစရိတ်',
  'Product cost price × quantity': 'ကုန်ပစ္စည်း ကုန်ကျစျေး × အရေအတွက်',
  'Gross Profit': 'အကြမ်းအမြတ်',
  'Revenue − COGS': 'ဝင်ငွေ − ကုန်ကျစရိတ်',
  'Expenses': 'ကုန်ကျစရိတ်များ',
  'Total operating expenses': 'လည်ပတ်ကုန်ကျစရိတ် စုစုပေါင်း',
  'Net Profit': 'အသားတင်အမြတ်',
  'Gross Profit − Expenses': 'အကြမ်းအမြတ် − ကုန်ကျစရိတ်',
  'Profit': 'အမြတ်',
  'Loss': 'အရှုံး',
  'Generate': 'ထုတ်ယူမည်',
  'Select a date range and click': 'ရက်ပိုင်းနှင့် click နှိပ်ပါ',
  'to view the report.': 'မှတ်တမ်းကြည့်ရှုရန်',
  'Income vs Expenses': 'ဝင်ငွေ နှင့် ကုန်ကျစရိတ်',
  'Breakdown': 'အသေးစိတ်ခွဲခြင်း',
  'Open Report': 'Report ဖွင့်မည်',
  'View income, expenses & net profit by date range': 'ရက်ပိုင်းအလိုက် ဝင်ငွေ၊ ကုန်ကျစရိတ်နှင့် အသားတင်အမြတ်ကြည့်မည်',
  'account(s)': 'အကောင့်',
  'P&L Statement': 'အမြတ်/အရှုံး ထုတ်ပြန်ချက်',

  'SSPD Manager': 'SSPD စီမံခန့်ခွဲမှု',
  'logo': 'လိုဂို',
  'Initialization Failure': 'စနစ်စတင်ရန် မအောင်မြင်ပါ',
  'The ERP system failed to load. This is usually caused by network issues or blocked external resources.': 'ERP စနစ်ကို ဖွင့်၍မရပါ။ များသောအားဖြင့် ကွန်ရက်ပြဿနာ သို့မဟုတ် ပြင်ပ resource ပိတ်ဆို့ထားခြင်းကြောင့် ဖြစ်နိုင်ပါသည်။',
  'Force Reload System': 'စနစ်ကို ပြန်ဖွင့်မည်',
  'Deleted': 'ဖျက်ပြီး',
  'Discount': 'လျှော့စျေး',
  'Payment': 'ငွေပေးချေမှု',
  'Other': 'အခြား',
  'Other Income': 'အခြားဝင်ငွေ',
  'In Stock': 'လက်ကျန်ရှိ',
  'Out of Stock': 'လက်ကျန်မရှိ',
  'Qty Only': 'အရေအတွက်သာ',
  'Grand Total': 'စုစုပေါင်း',
  'Debit (DR)': 'ဒက်ဘစ် (DR)',
  'Credit (CR)': 'ခရက်ဒစ် (CR)',
  'As of Date': 'ဤရက်စွဲအထိ',
  'Global Unit Registry': 'ယူနစ်မှတ်ပုံတင်စာရင်း',
  'Unit Label': 'ယူနစ်အမည်',
  'System standard unit.': 'စနစ်သတ်မှတ်ယူနစ်',
  'NO UNITS MATCHING YOUR SEARCH': 'ရှာဖွေမှုနှင့်ကိုက်ညီသော ယူနစ်မရှိပါ',
  'New Unit': 'ယူနစ်အသစ်',
  'Edit Unit': 'ယူနစ်ပြင်မည်',
  'Supplier Directory': 'ပေးသွင်းသူစာရင်း',
  'Partner Identity': 'ပါတနာအချက်အလက်',
  'Contact Info': 'ဆက်သွယ်ရန်အချက်အလက်',
  'Location': 'တည်နေရာ',
  'Outstanding Balance': 'ကျန်ငွေ',
  'Address not registered': 'လိပ်စာမထည့်ရသေးပါ',
  'No Suppliers Enlisted': 'ပေးသွင်းသူ မရှိသေးပါ',
  'Update Partner': 'ပါတနာပြင်မည်',
  'New Supplier Registry': 'ပေးသွင်းသူအသစ် မှတ်ပုံတင်မည်',
  'Procurement Identity & Finance': 'ဝယ်ယူရေးအချက်အလက်နှင့် ငွေကြေး',
  'Phone Number': 'ဖုန်းနံပါတ်',
  'Opening Balance (MMK)': 'ဖွင့်လက်ကျန် (MMK)',
  'Business Address': 'လုပ်ငန်းလိပ်စာ',
  'Current Debt Balance': 'လက်ရှိကြွေးကျန်',
  'Balances are updated via procurement transactions and payments.': 'ဝယ်ယူမှုနှင့် ငွေပေးချေမှုများမှ လက်ကျန်ကို အပ်ဒိတ်လုပ်ပါသည်။',
  'Create Account': 'အကောင့်ဖန်တီးမည်',
  'Edit Account': 'အကောင့်ပြင်မည်',
  'Selected': 'ရွေးထားသည်',
  '(Optional)': '(မဖြစ်မနေမဟုတ်)',
  'Voucher Print Settings': 'ဘောင်ချာပရင့်ဆက်တင်များ',
  'Paper Size': 'စက္ကူအရွယ်အစား',
  'Thank you for your business': 'ဝယ်ယူအားပေးမှုအတွက် ကျေးဇူးတင်ပါသည်',
  'Photo Scan Mode': 'ဓာတ်ပုံစကန်မုဒ်',
  'Photo': 'ဓာတ်ပုံ',
  'Camera Access Required': 'ကင်မရာခွင့်ပြုချက်လိုအပ်သည်',
  'Camera access denied': 'ကင်မရာခွင့်ပြုချက် ငြင်းပယ်ခံရသည်',
  'Camera unavailable': 'ကင်မရာမရရှိပါ',
  'Gross Sales': 'စုစုပေါင်းအရောင်း',
  'Cash + Bank': 'ငွေသား + ဘဏ်',
  'Cash in Hand': 'လက်ဝယ်ငွေသား',
  'Bank / Digital': 'ဘဏ် / ဒစ်ဂျစ်တယ်',
  'Cash & Bank Detail': 'ငွေသားနှင့်ဘဏ် အသေးစိတ်'
};

const orderedEntries = Object.entries(enToMy).sort((a, b) => b[0].length - a[0].length);
const originalTextByNode = new WeakMap<Text, string>();
const originalAttrsByElement = new WeakMap<Element, Map<string, string>>();

let activeLanguage: AppLanguage = 'en';
let observer: MutationObserver | null = null;

const escapeRegex = (value: string) => value.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');

const preserveWhitespace = (original: string, translatedCore: string) => {
  const prefix = original.match(/^\s*/)?.[0] || '';
  const suffix = original.match(/\s*$/)?.[0] || '';
  return `${prefix}${translatedCore}${suffix}`;
};

const translateEnglishToMyanmar = (text: string) => {
  const trimmed = text.trim();
  if (!trimmed) return text;

  let next = trimmed;
  orderedEntries.forEach(([en, my]) => {
    next = next.replace(new RegExp(escapeRegex(en), 'gi'), my);
  });

  return preserveWhitespace(text, next);
};

const isInsideNoTranslateScope = (node: Node) => {
  const element = node.nodeType === Node.ELEMENT_NODE ? node as Element : node.parentElement;
  return Boolean(element?.closest('[data-no-translate="true"]'));
};

const updateTextNode = (node: Text) => {
  if (!node.parentElement || isInsideNoTranslateScope(node)) return;
  if (NON_TRANSLATABLE_TAGS.has(node.parentElement.tagName)) return;

  const currentValue = node.nodeValue || '';
  const storedOriginal = originalTextByNode.get(node);

  let original = storedOriginal;
  if (activeLanguage === 'en') {
    if (!storedOriginal) {
      original = currentValue;
      originalTextByNode.set(node, original);
    } else {
      const expectedTranslated = translateEnglishToMyanmar(storedOriginal);
      if (currentValue === expectedTranslated) {
        original = storedOriginal;
      } else {
        original = currentValue;
        originalTextByNode.set(node, original);
      }
    }
  } else if (!storedOriginal) {
    original = currentValue;
    originalTextByNode.set(node, original);
  } else {
    const expectedTranslated = translateEnglishToMyanmar(storedOriginal);
    if (currentValue !== expectedTranslated) {
      original = currentValue;
      originalTextByNode.set(node, original);
    }
  }

  const base = original ?? currentValue;
  const target = activeLanguage === 'my' ? translateEnglishToMyanmar(base) : base;
  if (target !== currentValue) node.nodeValue = target;
};

const updateElementAttributes = (element: Element) => {
  if (isInsideNoTranslateScope(element) || NON_TRANSLATABLE_TAGS.has(element.tagName)) return;

  const attrStore = originalAttrsByElement.get(element) || new Map<string, string>();
  originalAttrsByElement.set(element, attrStore);

  const attrs: string[] = [...TRANSLATABLE_ATTRS];
  if (element instanceof HTMLInputElement && BUTTON_INPUT_TYPES.has(element.type)) {
    attrs.push('value');
  }

  attrs.forEach((attr) => {
    const current = element.getAttribute(attr);
    if (current === null) return;

    const storedOriginal = attrStore.get(attr);
    let original = storedOriginal;

    if (activeLanguage === 'en') {
      if (!storedOriginal) {
        original = current;
        attrStore.set(attr, original);
      } else {
        const expectedTranslated = translateEnglishToMyanmar(storedOriginal);
        if (current === expectedTranslated) {
          original = storedOriginal;
        } else {
          original = current;
          attrStore.set(attr, original);
        }
      }
    } else if (!storedOriginal) {
      original = current;
      attrStore.set(attr, original);
    } else {
      const expectedTranslated = translateEnglishToMyanmar(storedOriginal);
      if (current !== expectedTranslated) {
        original = current;
        attrStore.set(attr, original);
      }
    }

    const base = original ?? current;
    const target = activeLanguage === 'my' ? translateEnglishToMyanmar(base) : base;
    if (target !== current) element.setAttribute(attr, target);
  });
};

const traverseAndUpdate = (node: Node) => {
  if (node.nodeType === Node.TEXT_NODE) {
    updateTextNode(node as Text);
    return;
  }

  if (node.nodeType !== Node.ELEMENT_NODE) return;

  const element = node as Element;
  updateElementAttributes(element);
  if (NON_TRANSLATABLE_TAGS.has(element.tagName) || isInsideNoTranslateScope(element)) return;

  element.childNodes.forEach((child) => traverseAndUpdate(child));
};

const handleMutations: MutationCallback = (mutations) => {
  mutations.forEach((mutation) => {
    if (mutation.type === 'characterData') {
      updateTextNode(mutation.target as Text);
      return;
    }

    if (mutation.type === 'attributes') {
      updateElementAttributes(mutation.target as Element);
      return;
    }

    mutation.addedNodes.forEach((node) => traverseAndUpdate(node));
  });
};

export const initDomLanguageTranslator = (language: AppLanguage) => {
  activeLanguage = language;
  if (typeof document === 'undefined' || !document.body) return () => undefined;

  if (!observer) {
    observer = new MutationObserver(handleMutations);
    observer.observe(document.body, {
      subtree: true,
      childList: true,
      characterData: true,
      attributes: true,
      attributeFilter: ['placeholder', 'title', 'aria-label', 'alt', 'value']
    });
  }

  traverseAndUpdate(document.body);

  return () => {
    observer?.disconnect();
    observer = null;
  };
};

export const setDomLanguage = (language: AppLanguage) => {
  activeLanguage = language;
  if (typeof document === 'undefined' || !document.body) return;
  traverseAndUpdate(document.body);
};
