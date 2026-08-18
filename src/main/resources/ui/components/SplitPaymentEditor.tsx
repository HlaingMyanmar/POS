import React from 'react';
import { Plus, Trash2 } from 'lucide-react';
import { PaymentMethodDTO, PaymentTransactionDTO } from '../types';

type Props = {
  methods: PaymentMethodDTO[];
  payments: PaymentTransactionDTO[];
  onChange: (payments: PaymentTransactionDTO[]) => void;
  disabled?: boolean;
  label?: string;
};

const emptyLine = (): PaymentTransactionDTO => ({ paymentMethodId: 0, amount: 0, transactionNo: '' });

const SplitPaymentEditor: React.FC<Props> = ({ methods, payments, onChange, disabled = false, label = 'Split Payment' }) => {
  const rows = payments.length ? payments : [emptyLine()];
  const total = rows.reduce((sum, row) => sum + (Number(row.amount) || 0), 0);

  const update = (index: number, patch: Partial<PaymentTransactionDTO>) => {
    const next = rows.map((row, i) => (i === index ? { ...row, ...patch } : row));
    onChange(next);
  };

  const remove = (index: number) => {
    const next = rows.filter((_, i) => i !== index);
    onChange(next.length ? next : [emptyLine()]);
  };

  return (
    <div className="rounded-lg border border-slate-200 bg-slate-50 p-3 space-y-2">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <div className="min-w-0">
          <p className="text-xs font-bold text-slate-700">{label}</p>
          <p className="text-[11px] text-slate-500">Cash, KPay, Bank စသည်ဖြင့် တစ်ကြောင်းချင်းခွဲပေးနိုင်သည်။</p>
        </div>
        <button
          type="button"
          disabled={disabled}
          onClick={() => onChange([...rows, emptyLine()])}
          className="inline-flex items-center gap-1 px-2.5 py-1.5 rounded-lg bg-slate-900 text-white text-[11px] font-bold disabled:opacity-50"
        >
          <Plus size={13} /> Add
        </button>
      </div>

      {rows.map((row, index) => (
        <div key={index} className="grid grid-cols-1 gap-2 sm:grid-cols-[minmax(0,1fr)_120px_36px]">
          <select
            value={row.paymentMethodId || 0}
            disabled={disabled}
            onChange={(e) => update(index, { paymentMethodId: Number(e.target.value) || 0 })}
            className="px-3 py-2 bg-white border border-slate-200 rounded-lg text-sm focus:outline-none focus:border-indigo-400 disabled:opacity-50"
          >
            <option value={0}>Payment Method</option>
            {methods.map((method) => (
              <option key={method.id} value={method.id}>{method.methodName}</option>
            ))}
          </select>
          <input
            type="number"
            min="0"
            step="0.01"
            value={row.amount || ''}
            disabled={disabled}
            onChange={(e) => update(index, { amount: Number(e.target.value) || 0 })}
            placeholder="Amount"
            className="px-3 py-2 bg-white border border-slate-200 rounded-lg text-sm text-right font-bold focus:outline-none focus:border-indigo-400 disabled:opacity-50"
          />
          <input
            value={row.transactionNo || ''}
            disabled={disabled}
            onChange={(e) => update(index, { transactionNo: e.target.value })}
            placeholder="Transaction No"
            className="px-3 py-2 bg-white border border-slate-200 rounded-lg text-sm focus:outline-none focus:border-indigo-400 disabled:opacity-50 sm:col-span-2"
          />
          <button
            type="button"
            disabled={disabled || rows.length <= 1}
            onClick={() => remove(index)}
            className="inline-flex min-h-10 items-center justify-center rounded-lg border border-slate-200 bg-white text-rose-500 disabled:opacity-40 sm:col-start-3 sm:row-span-2 sm:row-start-1 sm:min-h-0"
          >
            <Trash2 size={14} />
          </button>
        </div>
      ))}

      <div className="flex justify-end text-xs font-bold text-slate-700">
        Total: {new Intl.NumberFormat('en-US', { maximumFractionDigits: 2 }).format(total)} Ks
      </div>
    </div>
  );
};

export default SplitPaymentEditor;
