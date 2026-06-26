export interface ApiResponse<T> {
  success: boolean;
  message: string;
  data: T;
}

export interface ChatMessageDTO {
  id?: number;
  senderUsername: string;
  senderName?: string;
  senderRole?: string;
  content: string;
  sentAt?: string;
}

export interface StaffReportDTO {
  staffId: number;
  staffName: string;
  staffRole: string;
  salesCount: number;
  salesAmount: number;
  serviceJobsCount: number;
  completedJobsCount: number;
  cancelledJobsCount: number;
  reworkJobsCount: number;
  inProgressJobsCount: number;
  serviceJobsAmount: number;
  completionRate: number;
}

export interface AuthResponse {
  accessToken: string;
  refreshToken?: string;
  username: string;
  name?: string;
  phone?: string;
  roles: string[];
  permissions: string[];
}

export enum ProductType {
  NEW        = 'New',
  SECOND     = 'Second',
  SECOND_NEW = 'Second_New',
}

export interface ProductDTO {
  id: number;
  productCode: string;
  name: string;
  currentStock: number;
  hasSerial?: boolean;
  stockQty?: number;
  reorderLevel?: number;
  productType: ProductType;
  sellingPrice: number;
  costPrice?: number;
  warrantyMonths?: number;
  categoryName?: string;
  brandName?: string;
  unitName?: string;
  availableSerialCount?: number;
  remark?: string;
  warrantyTerms?: string;
}

export interface CustomerDTO {
  id: number;
  name: string;
  phone: string;
  address: string;
}

export interface StaffDTO {
  id: number;
  name: string;
  role: string;
  active: boolean;
}

export interface PaymentMethodDTO {
  id: number;
  methodName: string;
  active: boolean;
}

export interface SaleDetailDTO {
  productId: number;
  productName?: string;
  qty: number;
  unitPrice: number;
  subtotal: number;
  discountAmount?: number;
  serialNumbers: string[];
  warrantyMonths?: number;
  warrantyExpiryDate?: string;
}

export interface SaleDTO {
  id?: number;
  saleCode?: string;
  customerId: number;
  customerName?: string;
  staffId: number;
  staffName?: string;
  paymentMethodName?: string;
  saleDate?: string;
  totalAmount?: number;
  discountAmount?: number;
  netAmount?: number;
  paidAmount?: number;
  dueAmount?: number;
  paymentStatus?: string;
  remark?: string;
  paymentMethodId?: number;
  details: SaleDetailDTO[];
}

export interface SaleReturnDetailDTO {
  returnId?: number;
  productId: number;
  productName?: string;
  qty: number;
  unitPrice: number;
  subtotal: number;
  serialNumbers?: string[];
}

export interface SaleReturnDTO {
  id?: number;
  saleId: number;
  saleCode?: string;
  customerId?: number;
  customerName?: string;
  staffId?: number;
  returnCode?: string;
  returnDate?: string;
  totalReturnAmount?: number;
  refundAmount?: number;
  paymentMethodId?: number;
  transactionNo?: string;
  reason?: string;
  details: SaleReturnDetailDTO[];
}

export interface BookingDetailDTO {
  id?: number;
  serviceTypeId?: number;
  serviceTypeName?: string;
  subServiceTypeId?: number;
  subServiceTypeName?: string;
  description?: string;
  estimatedCost?: number;
}

export interface BookingDTO {
  id?: number;
  invoiceNo?: string;
  customerId?: number;
  customerName?: string;
  customerPhone?: string;
  staffId?: number;
  staffName?: string;
  paymentMethodId?: number;
  paymentMethodName?: string;
  bookingDate?: string;
  appointmentDate?: string;
  totalAmount?: number;
  status?: string;
  remark?: string;
  deviceType?: string;
  brand?: string;
  model?: string;
  serialNumber?: string;
  color?: string;
  accessories?: string;
  shelfLocation?: string;
  details?: BookingDetailDTO[];
  devices?: {
    id?: number;
    deviceType?: string;
    brand?: string;
    model?: string;
    serialNumber?: string;
    color?: string;
    accessories?: string;
  }[];
}

export interface ServiceJobLineDTO {
  id?: number;
  // Backend response fields (GET)
  serviceItemId?: number;
  serviceItemName?: string;
  qty?: number;
  price?: number;
  subtotal?: number;
  warrantyMonths?: number;
  // Legacy / display helpers
  serviceTypeId?: number;
  serviceTypeName?: string;
  subServiceTypeId?: number;
  subServiceTypeName?: string;
  description?: string;
  cost?: number;
}

export interface ServiceJobDTO {
  id?: number;
  jobNo?: string;
  customerId?: number;
  customerName?: string;
  assignedStaffId?: number;
  assignedStaffName?: string;
  itemName?: string;
  itemCondition?: string;
  problemDesc?: string;
  diagnosisNotes?: string;
  estimatedCost?: number;
  finalCost?: number;
  discountAmount?: number;
  foc?: boolean;
  netAmount?: number;
  paidAmount?: number;
  dueAmount?: number;
  dueDate?: string;
  paymentStatus?: string;
  receivedDate?: string;
  estimatedCompletion?: string;
  completedDate?: string;
  deliveredDate?: string;
  status?: string;
  paymentMethodId?: number;
  paymentMethodName?: string;
  bookingId?: number;
  bookingNo?: string;
  rework?: boolean;
  parentJobNo?: string;
  remark?: string;
  lines?: ServiceJobLineDTO[];
  productParts?: ServiceJobPartDTO[];
}

export interface ServiceJobPartDTO {
  id?: number;
  productId: number;
  productName?: string;
  productCode?: string;
  productType?: string;
  qty: number;
  unitPrice: number;
  subtotal?: number;
  serialNumbers: string[];
}

export interface ServiceItemDTO {
  id: number;
  code?: string;
  item: string;
  price?: number;
  isActive?: boolean;
  serviceTypeId?: number;
  serviceTypeName?: string;
  subServiceTypeId?: number;
  subServiceTypeName?: string;
}

export interface SettleJobDTO {
  finalCost?: number;
  discountAmount?: number;
  foc?: boolean;
  paidAmount?: number;
  paymentMethodId?: number;
}

export interface ProductSerialDTO {
  id: number;
  serialNumber: string;
  status: string;
  productId: number;
  productName: string;
  warrantyMonths?: number;
  warrantyStartDate?: string;
  warrantyEndDate?: string;
  condition?: string;
  photoBase64?: string;
}

export interface ExpenseDTO {
  id?: number;
  expenseCode?: string;
  expenseDate?: string;
  accountId: number;
  accountName?: string;
  paymentMethodId?: number;
  paymentMethodName?: string;
  amount: number;
  description?: string;
  staffId?: number;
  staffName?: string;
}

// Cart item (local only, used during New Sale)
export interface CartItem {
  product: ProductDTO;
  qty: number;
  unitPrice: number;
  discountAmount: number;
  serialNumbers: string[];
}
