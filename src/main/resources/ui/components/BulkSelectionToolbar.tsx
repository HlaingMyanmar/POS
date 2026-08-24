import React from 'react';
import { CheckSquare, ChevronDown, Download, Printer, X } from 'lucide-react';

export type BulkAction<T> = {
  key: string;
  label: string;
  icon?: React.ReactNode;
  tone?: 'neutral' | 'indigo' | 'amber' | 'rose';
  dangerous?: boolean;
  allowed?: boolean;
  confirm?: (rows: T[]) => Promise<boolean> | boolean;
};

type Props<T> = {
  visibleCount: number;
  selectedCount: number;
  allVisibleSelected: boolean;
  someVisibleSelected: boolean;
  onToggleVisible: () => void;
  onClear: () => void;
  actions: BulkAction<T>[];
  selectedRows: T[];
  onAction: (action: BulkAction<T>, rows: T[]) => void;
  selectedTotal?: number;
  totalLabel?: string;
};

const toneClass = {
  neutral: 'border-slate-200 bg-white text-slate-600 hover:bg-slate-50',
  indigo: 'border-indigo-200 bg-indigo-50 text-indigo-700 hover:bg-indigo-100',
  amber: 'border-amber-200 bg-amber-50 text-amber-700 hover:bg-amber-100',
  rose: 'border-rose-200 bg-rose-50 text-rose-700 hover:bg-rose-100',
};

export function BulkSelectionToolbar<T>({
  visibleCount,
  selectedCount,
  allVisibleSelected,
  someVisibleSelected,
  onToggleVisible,
  onClear,
  actions,
  selectedRows,
  onAction,
  selectedTotal,
  totalLabel = 'Total',
}: Props<T>) {
  if (visibleCount === 0 && selectedCount === 0) return null;

  const availableActions = actions.filter((action) => action.allowed !== false);
  return (
    <div className="flex flex-col gap-2 rounded-xl border border-indigo-100 bg-indigo-50/70 px-3 py-2.5 text-left sm:flex-row sm:items-center sm:justify-between">
      <div className="flex flex-wrap items-center gap-2">
        <label className="inline-flex cursor-pointer items-center gap-2 text-xs font-bold text-indigo-800">
          <input
            type="checkbox"
            checked={allVisibleSelected}
            ref={(element) => { if (element) element.indeterminate = someVisibleSelected; }}
            onChange={onToggleVisible}
            className="h-4 w-4 accent-indigo-600"
          />
          <span>{allVisibleSelected ? 'Visible rows selected' : 'Select visible rows'}</span>
        </label>
        <span className="rounded-md bg-white px-2 py-1 text-[11px] font-black text-indigo-700 shadow-sm">{selectedCount} selected</span>
        <span className="text-[11px] text-indigo-600">of {visibleCount} visible</span>
        {selectedTotal !== undefined && selectedCount > 0 && <span className="rounded-md bg-white px-2 py-1 text-[11px] font-black text-emerald-700 shadow-sm">{totalLabel}: {selectedTotal.toLocaleString()} Ks</span>}
        {selectedCount > 0 && <button type="button" onClick={onClear} className="inline-flex items-center gap-1 text-[11px] font-bold text-slate-500 hover:text-rose-600"><X size={13} /> Clear</button>}
      </div>
      {selectedCount > 0 && availableActions.length > 0 && (
        <div className="flex flex-wrap items-center gap-2">
          {availableActions.map((action) => (
            <button
              key={action.key}
              type="button"
              title={action.dangerous ? 'Requires permission and confirmation' : action.label}
              onClick={async () => {
                if (action.confirm && !(await action.confirm(selectedRows))) return;
                onAction(action, selectedRows);
              }}
              className={`inline-flex items-center gap-1.5 rounded-lg border px-2.5 py-1.5 text-[11px] font-bold transition-colors ${toneClass[action.tone || 'neutral']}`}
            >
              {action.icon || <CheckSquare size={13} />}
              {action.label}
              {action.dangerous && <span className="text-[9px] uppercase">Protected</span>}
              <ChevronDown size={12} className="opacity-40" />
            </button>
          ))}
        </div>
      )}
    </div>
  );
}

export const safeBulkActions = {
  export: <><Download size={13} /> Export</>,
  print: <><Printer size={13} /> Print</>,
};
