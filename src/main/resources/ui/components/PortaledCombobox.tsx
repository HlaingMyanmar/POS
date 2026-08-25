import React, { useEffect, useLayoutEffect, useRef, useState } from 'react';
import { createPortal } from 'react-dom';

export type PortaledComboboxItem = {
  id: number;
  label: string;
  sub?: string;
  searchText?: string;
  disabled?: boolean;
};

type Props = {
  items: PortaledComboboxItem[];
  value: number;
  displayValue?: string;
  placeholder?: string;
  disabled?: boolean;
  onChange: (id: number, item?: PortaledComboboxItem) => void;
  inputClassName?: string;
  maxItems?: number;
};

/** Searchable dropdown rendered on document.body so modal/table overflow cannot clip it. */
const PortaledCombobox: React.FC<Props> = ({
  items,
  value,
  displayValue,
  placeholder = 'ရှာပါ...',
  disabled = false,
  onChange,
  inputClassName,
  maxItems = 80,
}) => {
  const inputRef = useRef<HTMLInputElement>(null);
  const [open, setOpen] = useState(false);
  const [search, setSearch] = useState('');
  const [menuPos, setMenuPos] = useState<{ top: number; left: number; width: number } | null>(null);
  const selected = items.find((item) => item.id === value);
  const shownLabel = displayValue ?? selected?.label ?? '';

  useEffect(() => {
    if (!open) setSearch(shownLabel);
  }, [shownLabel, open]);

  useLayoutEffect(() => {
    if (!open || !inputRef.current || disabled) {
      setMenuPos(null);
      return;
    }
    const place = () => {
      if (!inputRef.current) return;
      const rect = inputRef.current.getBoundingClientRect();
      const maxHeight = 224;
      const width = Math.min(Math.max(rect.width, 280), window.innerWidth - 16);
      const spaceBelow = window.innerHeight - rect.bottom - 8;
      const openUp = spaceBelow < 160 && rect.top > spaceBelow;
      const height = Math.min(maxHeight, openUp ? Math.max(120, rect.top - 8) : Math.max(120, spaceBelow));
      setMenuPos({
        top: openUp ? Math.max(8, rect.top - height - 4) : rect.bottom + 4,
        left: Math.max(8, Math.min(rect.left, window.innerWidth - width - 8)),
        width,
      });
    };
    place();
    window.addEventListener('scroll', place, true);
    window.addEventListener('resize', place);
    return () => {
      window.removeEventListener('scroll', place, true);
      window.removeEventListener('resize', place);
    };
  }, [open, search, value, disabled]);

  const query = search.trim().toLowerCase();
  const filtered = items.filter((item) =>
    !query || `${item.label} ${item.sub || ''} ${item.searchText || ''}`.toLowerCase().includes(query)
  );
  const choices = filtered.slice(0, maxItems);

  return (
    <div className="relative min-w-0 flex-1">
      <input
        ref={inputRef}
        value={open ? search : shownLabel}
        disabled={disabled}
        onChange={(event) => {
          const next = event.target.value;
          setSearch(next);
          setOpen(true);
          if (!next.trim()) onChange(0);
        }}
        onFocus={() => {
          if (disabled) return;
          setSearch('');
          setOpen(true);
        }}
        onBlur={() => setTimeout(() => setOpen(false), 150)}
        placeholder={shownLabel || placeholder}
        className={inputClassName || 'h-10 w-full rounded-lg border border-slate-200 bg-slate-50 px-3 text-sm focus:border-indigo-500 focus:outline-none focus:ring-2 focus:ring-indigo-500/20 disabled:cursor-not-allowed disabled:opacity-60'}
      />
      {open && menuPos && !disabled && createPortal(
        <div
          style={{ position: 'fixed', top: menuPos.top, left: menuPos.left, width: menuPos.width, zIndex: 9999 }}
          className="max-h-56 overflow-auto rounded-lg border border-slate-200 bg-white shadow-xl"
        >
          {choices.length ? choices.map((item) => (
            <button
              key={item.id}
              type="button"
              disabled={item.disabled}
              onMouseDown={(event) => {
                event.preventDefault();
                if (item.disabled) return;
                onChange(item.id, item);
                setSearch(item.label);
                setOpen(false);
              }}
              className={`w-full px-3 py-2 text-left ${item.disabled ? 'cursor-not-allowed bg-slate-50 opacity-55' : 'hover:bg-indigo-50'} ${value === item.id ? 'bg-indigo-50' : ''}`}
            >
              <p className="text-xs font-semibold text-slate-800 sm:text-sm">{item.label}</p>
              {item.sub && <p className="text-[10px] text-slate-400 sm:text-[11px]">{item.sub}</p>}
            </button>
          )) : (
            <p className="px-3 py-2.5 text-xs text-slate-400">ရှာမတွေ့ပါ</p>
          )}
          {filtered.length > choices.length && (
            <p className="sticky bottom-0 border-t border-slate-100 bg-slate-50 px-3 py-1.5 text-[10px] text-slate-500">
              {filtered.length} ခုထဲမှ {choices.length} ခု — ပိုရှာရန် စာရိုက်ပါ
            </p>
          )}
        </div>,
        document.body
      )}
    </div>
  );
};

export default PortaledCombobox;
