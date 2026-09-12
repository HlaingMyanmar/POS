import axios from 'axios';
import { BASE_URL } from './api';
import { ApiResponse } from '../types';

const TOKEN_KEY = 'sspd_customer_token';
const SESSION_KEY = 'sspd_customer_session';
const CART_KEY = 'sspd_customer_cart';

export type CustomerSession = {
  accessToken: string;
  customerId?: number;
  name?: string;
  phone?: string;
  address?: string;
  email?: string;
  needsProfile?: boolean;
};

export type CustomerAuthPayload = Partial<CustomerSession> & {
  accessToken?: string;
  resetSent?: boolean;
};

export type CatalogProduct = {
  id: number;
  name: string;
  productCode?: string;
  categoryName?: string;
  brandName?: string;
  productType?: string;
  sellingPrice?: number;
  warrantyMonths?: number;
  warrantyTerms?: string;
  inStock?: boolean;
  photoUrls?: string[];
  thumbnailUrl?: string;
};

export type CatalogOption = { id: number; name: string; parentId?: number; parentName?: string };
export type CatalogPage = { content: CatalogProduct[]; page: number; size: number; totalElements: number; hasNext: boolean };
export type CatalogQuery = { page?: number; size?: number; q?: string; categoryId?: number; brandId?: number; sort?: string };

export type CatalogService = {
  id: number;
  name: string;
  serviceTypeName?: string;
  price?: number;
  warrantyMonths?: number;
  description?: string;
};

export type CartLine = { product: CatalogProduct; qty: number };

export type CustomerPaymentChannel = {
  id: number;
  methodName?: string;
  payeeName?: string;
  payeeAccountNo?: string;
  payeeHint?: string;
};

export type DeliveryQuote = {
  state?: string;
  weightKg?: number | null;
  baseCharge?: number | null;
  extraCharge?: number | null;
  deliveryCharge?: number | null;
  reason?: string | null;
};

export type CustomerBranding = {
  companyName?: string;
  taglineMm?: string;
  pickupDepositPercent?: number;
  deliveryEnabled?: boolean;
};

export type CustomerOrder = {
  id: number;
  orderNo: string;
  orderType?: string;
  shippingState?: string;
  shippingVersion?: number;
  shippingRenegotiated?: boolean;
  shippingWeightKg?: number;
  deliveryCharge?: number;
  quotedDeliveryCharge?: number;
  itemsTotal?: number;
  shippingReason?: string;
  deliveryHandler?: string;
  status: string;
  note?: string;
  total?: number;
  createdAt?: string;
  lines?: { productId?: number; productName: string; qty: number; unitPrice: number; subtotal: number }[];
  paymentState?: string;
  paymentChoice?: string;
  paymentMethodId?: number;
  paymentMethodName?: string;
  payeeName?: string;
  payeeAccountNo?: string;
  paymentInstructions?: string;
  paymentReviewNote?: string;
  latestProofId?: number;
  collectionProofId?: number;
  collectionAmount?: number;
  collectionPaymentMethodName?: string;
  completedSaleId?: number;
  completedSale?: CustomerPurchase;
  depositPercent?: number;
  depositAmount?: number;
  remainingAmount?: number;
  townshipId?: number;
  townshipName?: string;
  wardId?: number;
  wardName?: string;
  deliveryLocationMode?: string;
  deliveryAddress?: string;
  deliveryPhone?: string;
  requestedDeliveryAt?: string;
  deliveryScheduledAt?: string;
  deliveryStatus?: string;
  deliveryMilestones?: {
    id?: number;
    fromStatus?: string;
    toStatus?: string;
    actor?: string;
    actorType?: string;
    note?: string;
    at?: string;
  }[];
  customerReceiptState?: string;
  customerReceivedAt?: string;
  customerReceiptNote?: string;
  awaitingCustomerReceipt?: boolean;
  canRate?: boolean;
  canEditRating?: boolean;
  promoCode?: string;
  discountAmount?: number;
  eligibleSubtotal?: number;
  rating?: {
    id?: number;
    productRating?: number;
    serviceRating?: number;
    rating?: number;
    comment?: string;
    review?: string;
    hidden?: boolean;
    editable?: boolean;
    editableUntil?: string;
  };
};

export type CustomerBooking = {
  id: number;
  bookingNo: string;
  status: string;
  complaintNote?: string;
  appointmentDate?: string;
  createdAt?: string;
};

export type CustomerPurchase = {
  id: number;
  saleCode: string;
  saleDate?: string;
  netAmount?: number;
  paymentStatus?: string;
  lines?: {
    productName: string;
    qty: number;
    unitPrice?: number;
    subtotal?: number;
    serialNumber?: string;
    warrantyMonths?: number;
    warrantyStartDate?: string;
    warrantyExpiryDate?: string;
    warrantyStatus?: string;
    warrantyDaysRemaining?: number;
  }[];
};

export type CustomerJob = {
  id: number;
  jobNo: string;
  status?: string;
  itemName?: string;
  deviceType?: string;
  problemDesc?: string;
  receivedDate?: string;
  netAmount?: number;
  paymentStatus?: string;
  services?: { name?: string; qty?: number }[];
  parts?: { productName?: string; qty?: number }[];
};

let customerToken: string | null = null;

const loadStoredSession = (): CustomerSession | null => {
  try {
    const raw = sessionStorage.getItem(SESSION_KEY);
    if (!raw) return null;
    const parsed = JSON.parse(raw) as CustomerSession;
    if (!parsed?.accessToken) return null;
    customerToken = parsed.accessToken;
    return parsed;
  } catch {
    return null;
  }
};

loadStoredSession();

export const customerApi = axios.create({
  baseURL: BASE_URL,
  headers: { 'Content-Type': 'application/json' },
});

customerApi.interceptors.request.use(config => {
  if (typeof FormData !== 'undefined' && config.data instanceof FormData) {
    const headers = config.headers as { delete?: (key: string) => void; [key: string]: unknown };
    if (typeof headers.delete === 'function') {
      headers.delete('Content-Type');
    } else {
      delete (config.headers as Record<string, unknown>)['Content-Type'];
      delete (config.headers as Record<string, unknown>)['content-type'];
    }
  }
  if (customerToken) config.headers.Authorization = `Bearer ${customerToken}`;
  return config;
});

customerApi.interceptors.response.use(
  response => response.data,
  error => {
    const url = String(error.config?.url || '');
    const isAuthCall = url.includes('/customer-portal/auth/');
    const data = error.response?.data;
    if (error.response?.status === 401 && !isAuthCall) {
      clearCustomerSession();
      if (typeof window !== 'undefined') {
        window.dispatchEvent(new Event('customer-session-ended'));
      }
    }
    const message = (data && typeof data === 'object' && (data.message || data.error))
      || error.message
      || 'Operation failed';
    return Promise.reject({ success: false, message, ...((data && typeof data === 'object') ? data : {}) });
  }
);

export const getCustomerSession = (): CustomerSession | null => loadStoredSession();

export const saveCustomerSession = (session: CustomerSession) => {
  customerToken = session.accessToken;
  sessionStorage.setItem(TOKEN_KEY, session.accessToken);
  sessionStorage.setItem(SESSION_KEY, JSON.stringify(session));
};

export const clearCustomerSession = () => {
  customerToken = null;
  sessionStorage.removeItem(TOKEN_KEY);
  sessionStorage.removeItem(SESSION_KEY);
};

export const loadCart = (): CartLine[] => {
  try {
    const raw = sessionStorage.getItem(CART_KEY);
    return raw ? JSON.parse(raw) as CartLine[] : [];
  } catch {
    return [];
  }
};

export const saveCart = (lines: CartLine[]) => {
  sessionStorage.setItem(CART_KEY, JSON.stringify(lines));
};

export const customerPortalService = {
  register: (body: { name: string; phone: string; email: string; password: string; address?: string }) =>
    customerApi.post<any, ApiResponse<CustomerAuthPayload>>('/v1/customer-portal/auth/register', body),
  login: (body: { phone?: string; login?: string; password: string }) =>
    customerApi.post<any, ApiResponse<CustomerAuthPayload>>('/v1/customer-portal/auth/login', body),
  forgotPassword: (body: { email: string }) =>
    customerApi.post<any, ApiResponse<CustomerAuthPayload>>('/v1/customer-portal/auth/forgot', body),
  resetPassword: (body: { token: string; password: string; googleIdToken: string }) =>
    customerApi.post<any, ApiResponse<CustomerAuthPayload>>('/v1/customer-portal/auth/reset', body),
  me: () => customerApi.get<any, ApiResponse<CustomerSession>>('/v1/customer-portal/me'),
  updateProfile: (body: { name?: string; phone: string; address?: string }) =>
    customerApi.put<any, ApiResponse<CustomerSession>>('/v1/customer-portal/me', body),
  productPage: (params: CatalogQuery, signal?: AbortSignal) =>
    customerApi.get<any, ApiResponse<CatalogPage>>('/v1/customer-portal/catalog/products/page', { params, signal }),
  categories: () => customerApi.get<any, ApiResponse<CatalogOption[]>>('/v1/customer-portal/catalog/categories'),
  brands: () => customerApi.get<any, ApiResponse<CatalogOption[]>>('/v1/customer-portal/catalog/brands'),
  products: () => customerApi.get<any, ApiResponse<CatalogProduct[]>>('/v1/customer-portal/catalog/products'),
  services: () => customerApi.get<any, ApiResponse<CatalogService[]>>('/v1/customer-portal/catalog/services'),
  branding: () => customerApi.get<any, ApiResponse<CustomerBranding>>('/v1/customer-portal/branding'),
  deliveryLocations: () => customerApi.get<any, ApiResponse<any[]>>('/v1/customer-portal/delivery-locations'),
  deliveryQuote: (body: {
    orderType: string;
    wardId?: number;
    lines: { productId: number; qty: number }[];
  }, signal?: AbortSignal) =>
    customerApi.post<any, ApiResponse<DeliveryQuote>>('/v1/customer-portal/delivery-quote', body, { signal }),
  placeOrder: (body: {
    orderType?: string;
    paymentChoice?: string;
    requestedDeliveryAt?: string;
    wardId?: number;
    townshipId?: number;
    deliveryLocationMode?: string;
    deliveryAddress?: string;
    deliveryPhone?: string;
    note?: string;
    idempotencyKey: string;
    lines: { productId: number; qty: number }[];
    promoCode?: string;
  }) =>
    customerApi.post<any, ApiResponse<CustomerOrder>>('/v1/customer-portal/orders', body),
  myOrders: () => customerApi.get<any, ApiResponse<CustomerOrder[]>>('/v1/customer-portal/orders'),
  paymentChannels: () =>
    customerApi.get<any, ApiResponse<CustomerPaymentChannel[]>>('/v1/customer-portal/payment-channels'),
  choosePayment: (orderId: number, paymentChoice: string) =>
    customerApi.post<any, ApiResponse<CustomerOrder>>(`/v1/customer-portal/orders/${orderId}/payment-choice`, { paymentChoice }),
  choosePaymentChannel: (orderId: number, paymentMethodId: number) =>
    customerApi.post<any, ApiResponse<CustomerOrder>>(`/v1/customer-portal/orders/${orderId}/payment-channel`, { paymentMethodId }),
  decideShipping: (orderId: number, body: { version?: number; accept: boolean }) =>
    customerApi.post<any, ApiResponse<CustomerOrder>>(`/v1/customer-portal/orders/${orderId}/shipping-decision`, body),
  confirmReceipt: (orderId: number, received: boolean, note?: string) =>
    customerApi.post<any, ApiResponse<CustomerOrder>>(`/v1/customer-portal/orders/${orderId}/receipt`, { received, note }),
  paymentProofs: (orderId: number) =>
    customerApi.get<any, ApiResponse<{ deposit?: any; remainder?: any }>>(`/v1/customer-portal/orders/${orderId}/payment-proofs`),
  submitPaymentProof: (orderId: number, data: FormData) =>
    customerApi.post<any, ApiResponse<CustomerOrder>>(`/v1/customer-portal/orders/${orderId}/payment-proof`, data),
  downloadOrderInvoice: async (orderId: number) => {
    const blob = await customerApi.get<any, Blob>(`/v1/customer-portal/orders/${orderId}/invoice.pdf`, { responseType: 'blob' });
    return blob;
  },
  downloadPurchaseInvoice: async (saleId: number) => {
    const blob = await customerApi.get<any, Blob>(`/v1/customer-portal/history/purchases/${saleId}/invoice.pdf`, { responseType: 'blob' });
    return blob;
  },
  requestService: (body: { serviceName?: string; deviceName?: string; problem?: string }) =>
    customerApi.post<any, ApiResponse<CustomerBooking>>('/v1/customer-portal/bookings', body),
  myBookings: () => customerApi.get<any, ApiResponse<CustomerBooking[]>>('/v1/customer-portal/bookings'),
  myPurchases: () => customerApi.get<any, ApiResponse<CustomerPurchase[]>>('/v1/customer-portal/history/purchases'),
  myWarranties: () => customerApi.get<any, ApiResponse<any[]>>('/v1/customer-portal/history/warranties'),
  myJobs: () => customerApi.get<any, ApiResponse<CustomerJob[]>>('/v1/customer-portal/history/jobs'),
  myReturns: (orderId?: number) =>
    orderId
      ? customerApi.get<any, ApiResponse<any[]>>(`/v1/customer-portal/orders/${orderId}/returns`)
      : customerApi.get<any, ApiResponse<any[]>>('/v1/customer-portal/returns'),
  submitReturn: (orderId: number, body: { reason: string; note?: string; saleId?: number; lines: { productId: number; qty: number; serialNumber?: string }[] }, files?: File[]) => {
    if (!files?.length) {
      return customerApi.post<any, ApiResponse<any>>(`/v1/customer-portal/orders/${orderId}/returns`, body);
    }
    const data = new FormData();
    data.append('body', new Blob([JSON.stringify(body)], { type: 'application/json' }));
    files.forEach((file) => data.append('photos', file));
    return customerApi.post<any, ApiResponse<any>>(`/v1/customer-portal/orders/${orderId}/returns/photos`, data);
  },
  rateOrder: (orderId: number, body: {
    productRating: number;
    serviceRating: number;
    comment?: string;
    products?: { productId: number; rating: number }[];
  }) =>
    customerApi.post<any, ApiResponse<any>>(`/v1/customer-portal/orders/${orderId}/rate`, body),
  validatePromo: (body: { code: string; lines: { productId: number; qty: number }[] }) =>
    customerApi.post<any, ApiResponse<{
      promoCode?: string;
      discountAmount?: number;
      discountPercent?: number;
      eligibleSubtotal?: number;
    }>>('/v1/customer-portal/promo-code/validate', body),
  wishlist: () => customerApi.get<any, ApiResponse<CatalogProduct[]>>('/v1/customer-portal/wishlist'),
  wishlistIds: () => customerApi.get<any, ApiResponse<number[]>>('/v1/customer-portal/wishlist/ids'),
  toggleWishlist: (productId: number) =>
    customerApi.post<any, ApiResponse<{ productId: number; wishlisted: boolean }>>('/v1/customer-portal/wishlist', { productId }),
  addWishlist: (productId: number) =>
    customerApi.put<any, ApiResponse<{ productId: number; wishlisted: boolean }>>('/v1/customer-portal/wishlist', { productId }),
  removeWishlist: (productId: number) =>
    customerApi.delete<any, ApiResponse<{ productId: number; wishlisted: boolean }>>(`/v1/customer-portal/wishlist/${productId}`),
};

