import { useEffect, useRef } from 'react';
import { useLocation } from 'react-router-dom';

/**
 * Keep-alive tab system တွင် tab active ဖြစ်တိုင်း refresh function ကို ခေါ်ပေးသည်။
 * Component mount ဖြစ်သောအချိန်တွင် path ကို မှတ်ထားပြီး၊
 * အဆိုပါ path သို့ ပြန်ရောက်လာတိုင်း refresh() ကို run သည်။
 */
export const useRefreshOnTabActivate = (refresh: () => void): void => {
  const location = useLocation();
  const mountedPath = useRef(location.pathname);
  const wasHidden = useRef(false);
  // Always keep ref to latest refresh fn to avoid stale closure
  const refreshRef = useRef(refresh);
  refreshRef.current = refresh;

  useEffect(() => {
    if (location.pathname !== mountedPath.current) {
      // Navigated away — mark as hidden
      wasHidden.current = true;
    } else if (wasHidden.current) {
      // Came back to our tab after being hidden — refresh
      wasHidden.current = false;
      refreshRef.current();
    }
  }, [location.pathname]);
};
