import React, { useEffect } from 'react';
import { X, Keyboard } from 'lucide-react';
import { NAV_SHORTCUTS } from '../hooks/useKeyboardShortcuts';

interface Props {
  onClose: () => void;
  isDark: boolean;
}

const Kbd: React.FC<{ children: React.ReactNode }> = ({ children }) => (
  <span className="inline-flex items-center px-2 py-0.5 rounded border border-slate-300 bg-slate-100 text-slate-700 text-xs font-mono font-bold shadow-sm">
    {children}
  </span>
);

const KeyboardShortcutsHelp: React.FC<Props> = ({ onClose, isDark }) => {
  useEffect(() => {
    const handle = (e: KeyboardEvent) => {
      if (e.key === 'Escape') onClose();
    };
    window.addEventListener('keydown', handle);
    return () => window.removeEventListener('keydown', handle);
  }, [onClose]);

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-slate-900/60 backdrop-blur-sm"
      onClick={onClose}
    >
      <div
        className={`w-full max-w-sm rounded-2xl shadow-2xl overflow-hidden ${isDark ? 'bg-slate-800 text-slate-100' : 'bg-white text-slate-800'}`}
        onClick={e => e.stopPropagation()}
      >
        <div className={`px-5 py-4 flex items-center justify-between border-b ${isDark ? 'border-slate-700' : 'border-slate-100'}`}>
          <div className="flex items-center gap-2">
            <Keyboard size={16} className="text-indigo-500" />
            <span className="font-bold text-sm">Keyboard Shortcuts</span>
          </div>
          <button
            onClick={onClose}
            className={`p-1.5 rounded-lg ${isDark ? 'hover:bg-slate-700 text-slate-400' : 'hover:bg-slate-100 text-slate-500'}`}
          >
            <X size={16} />
          </button>
        </div>

        <div className="p-5 space-y-5">
          <div>
            <p className={`text-[10px] uppercase font-bold tracking-widest mb-3 ${isDark ? 'text-slate-400' : 'text-slate-500'}`}>
              Navigation
            </p>
            <div className="space-y-2.5">
              {NAV_SHORTCUTS.map(s => (
                <div key={s.key} className="flex items-center justify-between">
                  <span className={`text-sm ${isDark ? 'text-slate-300' : 'text-slate-600'}`}>{s.label}</span>
                  <div className="flex items-center gap-1">
                    <Kbd>Alt</Kbd>
                    <span className={`text-xs ${isDark ? 'text-slate-500' : 'text-slate-400'}`}>+</span>
                    <Kbd>{s.key}</Kbd>
                  </div>
                </div>
              ))}
            </div>
          </div>

          <div>
            <p className={`text-[10px] uppercase font-bold tracking-widest mb-3 ${isDark ? 'text-slate-400' : 'text-slate-500'}`}>
              General
            </p>
            <div className="space-y-2.5">
              <div className="flex items-center justify-between">
                <span className={`text-sm ${isDark ? 'text-slate-300' : 'text-slate-600'}`}>Show shortcuts</span>
                <Kbd>?</Kbd>
              </div>
              <div className="flex items-center justify-between">
                <span className={`text-sm ${isDark ? 'text-slate-300' : 'text-slate-600'}`}>Close / Dismiss</span>
                <Kbd>Esc</Kbd>
              </div>
            </div>
          </div>
        </div>

        <div className={`px-5 py-3 text-center border-t ${isDark ? 'border-slate-700 bg-slate-900/40' : 'border-slate-100 bg-slate-50'}`}>
          <p className={`text-[10px] ${isDark ? 'text-slate-500' : 'text-slate-400'}`}>
            Press <strong className={isDark ? 'text-slate-300' : 'text-slate-600'}>?</strong> anytime to open this panel
          </p>
        </div>
      </div>
    </div>
  );
};

export default KeyboardShortcutsHelp;
