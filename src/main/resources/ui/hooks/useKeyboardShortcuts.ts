import { useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { AppRoute } from '../types';

export const NAV_SHORTCUTS: Array<{ key: string; route: AppRoute; label: string }> = [
  { key: '1', route: AppRoute.DASHBOARD,    label: 'Dashboard' },
  { key: '2', route: AppRoute.SALES,        label: 'Sales' },
  { key: '3', route: AppRoute.PURCHASES,    label: 'Purchases' },
  { key: '4', route: AppRoute.BOOKINGS,     label: 'Bookings' },
  { key: '5', route: AppRoute.SERVICE_JOBS, label: 'Service Jobs' },
  { key: '6', route: AppRoute.PRODUCTS,     label: 'Products' },
  { key: '7', route: AppRoute.PROFIT_LOSS,  label: 'Profit & Loss' },
];

export function useKeyboardShortcuts(onShowHelp: () => void) {
  const navigate = useNavigate();

  useEffect(() => {
    const handleKeyDown = (e: KeyboardEvent) => {
      const tag = (e.target as HTMLElement).tagName;
      if (['INPUT', 'TEXTAREA', 'SELECT'].includes(tag)) return;

      if (e.key === '?' && !e.altKey && !e.ctrlKey && !e.metaKey) {
        onShowHelp();
        return;
      }

      if (e.altKey && !e.ctrlKey && !e.metaKey) {
        const match = NAV_SHORTCUTS.find(s => s.key === e.key);
        if (match) {
          e.preventDefault();
          navigate(match.route);
        }
      }
    };

    window.addEventListener('keydown', handleKeyDown);
    return () => window.removeEventListener('keydown', handleKeyDown);
  }, [navigate, onShowHelp]);
}
