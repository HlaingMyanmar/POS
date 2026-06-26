import React, { useCallback, useEffect, useState, useMemo } from 'react';
import { useDataEvents } from '../../hooks/useDataEvents';
import { productService } from '../../services/productapiservice';
import { ProductDTO } from '../../types';
import { Package, AlertTriangle, BarChart2 } from 'lucide-react';

const fmt = (v: any) => Number(v ?? 0).toLocaleString();

export default function StockReport() {
  const [products, setProducts] = useState<ProductDTO[]>([]);
  const [loading,  setLoading]  = useState(true);
  const [search,   setSearch]   = useState('');

  const loadProducts = useCallback(() => {
    productService.getAll().then(data => setProducts(data)).catch(() => {}).finally(() => setLoading(false));
  }, []);

  useEffect(() => { loadProducts(); }, [loadProducts]);
  useDataEvents(['Product', 'Stock', 'Sale'], loadProducts);

  const stats = useMemo(() => {
    const total    = products.length;
    const inStock  = products.filter(p => (p.stockQty ?? p.currentStock ?? 0) > 0).length;
    const outStock = products.filter(p => (p.stockQty ?? p.currentStock ?? 0) === 0).length;
    const lowStock = products.filter(p => {
      const qty = p.stockQty ?? p.currentStock ?? 0;
      const rl  = p.reorderLevel ?? 0;
      return rl > 0 && qty <= rl;
    }).length;
    const totalValue = products.reduce((sum, p) => {
      const qty  = p.stockQty ?? p.currentStock ?? 0;
      const cost = p.costPrice ?? p.sellingPrice ?? 0;
      return sum + qty * cost;
    }, 0);
    const retailValue = products.reduce((sum, p) => {
      const qty = p.stockQty ?? p.currentStock ?? 0;
      return sum + qty * (p.sellingPrice ?? 0);
    }, 0);

    // by category
    const catMap: Record<string, { qty: number; count: number }> = {};
    products.forEach(p => {
      const cat = p.categoryName ?? 'Uncategorized';
      if (!catMap[cat]) catMap[cat] = { qty: 0, count: 0 };
      catMap[cat].qty   += p.stockQty ?? p.currentStock ?? 0;
      catMap[cat].count += 1;
    });

    return { total, inStock, outStock, lowStock, totalValue, retailValue, byCategory: Object.entries(catMap).sort((a,b) => b[1].qty - a[1].qty) };
  }, [products]);

  const filtered = useMemo(() => {
    const q = search.toLowerCase().trim();
    if (!q) return products;
    return products.filter(p =>
      p.name.toLowerCase().includes(q) ||
      p.productCode.toLowerCase().includes(q) ||
      (p.categoryName ?? '').toLowerCase().includes(q)
    );
  }, [products, search]);

  return (
    <div className="p-4 md:p-6 space-y-4">
      <div className="flex items-center gap-3">
        <Package size={22} className="text-cyan-500" />
        <h1 className="text-lg font-black text-slate-800">Stock Report</h1>
      </div>

      {loading ? (
        <div className="text-center py-10 text-slate-400">Loading...</div>
      ) : (
        <>
          {/* KPI cards */}
          <div className="grid grid-cols-2 md:grid-cols-3 lg:grid-cols-6 gap-3">
            {[
              { label: 'Total Products', value: fmt(stats.total),      color: 'text-slate-700',  bg: 'bg-slate-50'    },
              { label: 'In Stock',       value: fmt(stats.inStock),     color: 'text-emerald-700',bg: 'bg-emerald-50'  },
              { label: 'Out of Stock',   value: fmt(stats.outStock),    color: 'text-rose-700',   bg: 'bg-rose-50'     },
              { label: 'Low Stock',      value: fmt(stats.lowStock),    color: 'text-amber-700',  bg: 'bg-amber-50'    },
              { label: 'Cost Value',     value: fmt(stats.totalValue) + ' Ks', color: 'text-indigo-700', bg: 'bg-indigo-50' },
              { label: 'Retail Value',   value: fmt(stats.retailValue) + ' Ks', color: 'text-violet-700', bg: 'bg-violet-50' },
            ].map(k => (
              <div key={k.label} className={`rounded-xl p-3 border border-slate-200 ${k.bg}`}>
                <div className={`text-base font-black ${k.color}`}>{k.value}</div>
                <div className="text-[10px] text-slate-500 mt-0.5 font-semibold uppercase tracking-wide">{k.label}</div>
              </div>
            ))}
          </div>

          <div className="grid grid-cols-1 lg:grid-cols-3 gap-4">
            {/* By Category */}
            <div className="bg-white border border-slate-200 rounded-xl overflow-hidden">
              <div className="px-4 py-3 border-b font-semibold text-sm text-slate-700 flex items-center gap-2"><BarChart2 size={15} /> By Category</div>
              <div className="p-3 space-y-2">
                {stats.byCategory.map(([cat, v]) => {
                  const maxQty = stats.byCategory[0]?.[1]?.qty || 1;
                  const pct = Math.round((v.qty / maxQty) * 100);
                  return (
                    <div key={cat}>
                      <div className="flex justify-between text-xs mb-0.5">
                        <span className="font-medium text-slate-700 truncate max-w-[160px]">{cat}</span>
                        <span className="font-bold text-indigo-700">{fmt(v.qty)} pcs</span>
                      </div>
                      <div className="bg-slate-100 rounded-full h-1.5">
                        <div className="bg-cyan-500 h-1.5 rounded-full" style={{ width: `${pct}%` }} />
                      </div>
                    </div>
                  );
                })}
              </div>
            </div>

            {/* Product table */}
            <div className="bg-white border border-slate-200 rounded-xl overflow-hidden lg:col-span-2">
              <div className="px-4 py-3 border-b flex items-center justify-between">
                <span className="font-semibold text-sm text-slate-700">Product Stock List</span>
                <input type="text" placeholder="Search..." value={search} onChange={e => setSearch(e.target.value)}
                  className="border rounded-lg px-3 py-1 text-xs w-40" />
              </div>
              <div className="overflow-auto max-h-80">
                <table className="w-full text-sm">
                  <thead className="sticky top-0">
                    <tr>
                      <th className="px-3 py-2 text-left">Product</th>
                      <th className="px-3 py-2 text-right">Stock</th>
                      <th className="px-3 py-2 text-right">Reorder</th>
                      <th className="px-3 py-2 text-right">Status</th>
                    </tr>
                  </thead>
                  <tbody>
                    {filtered.map((p, i) => {
                      const qty = p.stockQty ?? p.currentStock ?? 0;
                      const rl  = p.reorderLevel ?? 0;
                      const isLow = rl > 0 && qty <= rl;
                      const isOut = qty === 0;
                      return (
                        <tr key={p.id} className={`border-t ${i % 2 === 0 ? '' : 'bg-slate-50'}`}>
                          <td className="px-3 py-2">
                            <div className="font-semibold text-slate-700 text-xs">{p.name}</div>
                            <div className="text-[10px] text-slate-400">{p.productCode}</div>
                          </td>
                          <td className="px-3 py-2 text-right font-bold text-slate-700">{fmt(qty)}</td>
                          <td className="px-3 py-2 text-right text-slate-400 text-xs">{rl > 0 ? rl : '-'}</td>
                          <td className="px-3 py-2 text-right">
                            {isOut
                              ? <span className="text-[9px] font-black px-1.5 py-0.5 bg-rose-100 text-rose-600 rounded-full">OUT</span>
                              : isLow
                              ? <span className="text-[9px] font-black px-1.5 py-0.5 bg-amber-100 text-amber-600 rounded-full flex items-center gap-0.5 justify-end"><AlertTriangle size={8} />LOW</span>
                              : <span className="text-[9px] font-black px-1.5 py-0.5 bg-emerald-100 text-emerald-600 rounded-full">OK</span>
                            }
                          </td>
                        </tr>
                      );
                    })}
                  </tbody>
                </table>
              </div>
            </div>
          </div>
        </>
      )}
    </div>
  );
}
