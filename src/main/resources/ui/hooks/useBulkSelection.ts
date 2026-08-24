import { useEffect, useMemo, useState } from 'react';

export type Identifiable = { id: number };

export const useBulkSelection = <T extends Identifiable>(visibleRows: T[]) => {
  const [selectedIds, setSelectedIds] = useState<Set<number>>(new Set());
  const visibleIds = useMemo(() => new Set(visibleRows.map((row) => row.id)), [visibleRows]);

  useEffect(() => {
    setSelectedIds((current) => {
      const next = new Set([...current].filter((id) => visibleIds.has(id)));
      return next.size === current.size ? current : next;
    });
  }, [visibleIds]);

  const toggle = (id: number) => {
    setSelectedIds((current) => {
      const next = new Set(current);
      if (next.has(id)) next.delete(id); else next.add(id);
      return next;
    });
  };

  const selectVisible = () => setSelectedIds(new Set(visibleRows.map((row) => row.id)));
  const clear = () => setSelectedIds(new Set());
  const allVisibleSelected = visibleRows.length > 0 && visibleRows.every((row) => selectedIds.has(row.id));
  const someVisibleSelected = visibleRows.some((row) => selectedIds.has(row.id)) && !allVisibleSelected;
  const selectedRows = visibleRows.filter((row) => selectedIds.has(row.id));

  return {
    selectedIds,
    selectedRows,
    selectedCount: selectedIds.size,
    allVisibleSelected,
    someVisibleSelected,
    toggle,
    selectVisible,
    clear,
  };
};
