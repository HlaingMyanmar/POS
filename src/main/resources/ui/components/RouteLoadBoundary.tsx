import React, { Suspense } from 'react';
import { useLocation } from 'react-router-dom';

const RouteLoading: React.FC = () => (
  <div className="flex min-h-48 items-center justify-center" role="status" aria-live="polite">
    <div className="flex flex-col items-center gap-3 text-slate-500">
      <div className="h-8 w-8 animate-spin rounded-full border-2 border-slate-200 border-t-indigo-600" />
      <span className="text-sm font-semibold">Loading page…</span>
    </div>
  </div>
);

interface RouteErrorBoundaryProps {
  children: React.ReactNode;
}

interface RouteErrorBoundaryState {
  error: Error | null;
}

class RouteErrorBoundary extends React.Component<RouteErrorBoundaryProps, RouteErrorBoundaryState> {
  declare readonly props: RouteErrorBoundaryProps;
  state: RouteErrorBoundaryState = { error: null };

  static getDerivedStateFromError(error: Error): RouteErrorBoundaryState {
    return { error };
  }

  componentDidCatch(error: Error): void {
    console.error('Route module failed to load', error);
  }

  render(): React.ReactNode {
    if (!this.state.error) return this.props.children;

    return (
      <div className="mx-auto my-8 max-w-lg rounded-xl border border-rose-200 bg-rose-50 p-6 text-center text-rose-900">
        <h2 className="text-lg font-bold">This page could not be loaded</h2>
        <p className="mt-2 text-sm">
          The application may have been updated or the network connection was interrupted.
        </p>
        <button
          type="button"
          onClick={() => window.location.reload()}
          className="mt-4 rounded-lg bg-rose-600 px-4 py-2 text-sm font-bold text-white hover:bg-rose-700"
        >
          Reload application
        </button>
      </div>
    );
  }
}

const RouteLoadBoundary: React.FC<RouteErrorBoundaryProps> = ({ children }) => {
  const location = useLocation();
  return (
    <RouteErrorBoundary key={location.pathname}>
      <Suspense fallback={<RouteLoading />}>{children}</Suspense>
    </RouteErrorBoundary>
  );
};

export default RouteLoadBoundary;
