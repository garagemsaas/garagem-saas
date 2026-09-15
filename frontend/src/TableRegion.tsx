import type { ReactNode } from 'react';

/** Keeps wide tables scrollable by touch and keyboard without widening the page. */
export function TableRegion({ label, children }: { label: string; children: ReactNode }) {
  return <div className="table-scroll" role="region" aria-label={label} tabIndex={0}>{children}</div>;
}
