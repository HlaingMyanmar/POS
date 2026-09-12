import React from 'react';

export default function ServiceJobWorkLogPanel({ assignments }: any) {
  const rows = assignments.flatMap((assignment: any) => (assignment.logs || []).map((log: any) => ({ ...log, staffName: assignment.staffName })))
    .sort((a: any, b: any) => String(b.occurredAt || '').localeCompare(String(a.occurredAt || '')));
  if (!rows.length) return null;
  return <section><h3 className="mb-3 font-black">Technician Work Log</h3><div className="space-y-2">{rows.map((log: any) =>
    <div key={`${log.staffName}-${log.id}`} className="rounded-xl border bg-slate-50 p-3 text-sm">
      <div className="flex flex-wrap justify-between gap-2"><b>{log.staffName} · {log.action}</b><span className="text-xs text-slate-400">{log.occurredAt ? new Date(log.occurredAt).toLocaleString() : ''}</span></div>
      {log.completedWork && <p className="mt-2"><b>လုပ်ပြီးသောအလုပ်:</b> {log.completedWork}</p>}
      {log.serviceDetails && <p className="mt-1"><b>Service:</b> {log.serviceDetails}</p>}
      {log.partsDetails && <p className="mt-1"><b>Parts:</b> {log.partsDetails}</p>}
      {log.note && <p className="mt-1 text-slate-600"><b>မှတ်ချက်:</b> {log.note}</p>}
    </div>)}</div></section>;
}
