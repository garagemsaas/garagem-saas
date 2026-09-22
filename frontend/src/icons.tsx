import {
  ArrowLeft, ArrowRight, CalendarDays, CarFront,
  Check, CheckCircle2, ChevronRight, CircleAlert, CircleHelp, Clock3,
  ClipboardList, LayoutDashboard, LogOut, Menu,
  Plus, Search, Settings2, ShieldCheck, TrendingUp, TriangleAlert,
  UserRound, UsersRound, Wrench, X,
} from "lucide-react";

const icons = {
  orders: ClipboardList, clients: UsersRound, vehicles: CarFront,
  team: UsersRound, search: Search, plus: Plus, arrow: ChevronRight,
  back: ArrowLeft, logout: LogOut, close: X, check: Check,
  overview: LayoutDashboard, calendar: CalendarDays,
  recovery: TrendingUp, settings: Settings2, menu: Menu,
  profile: UserRound, clock: Clock3, warning: TriangleAlert,
  critical: CircleAlert, good: CheckCircle2, info: CircleHelp,
  security: ShieldCheck, forward: ArrowRight, work: Wrench,
};
export type IconName = keyof typeof icons;

export function Icon({ name, size = 20 }: { name: IconName; size?: number }) {
  const Component = icons[name];
  return <Component size={size} strokeWidth={1.65} aria-hidden="true" />;
}
