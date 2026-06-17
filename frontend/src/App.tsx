import { Navigate, NavLink, Outlet, Route, Routes } from 'react-router-dom';
import CrawlResults from './components/CrawlResults';
import JobDashboard from './components/JobDashboard';
import ScheduleConfig from './components/ScheduleConfig';
import { useAuth } from './auth/AuthProvider';

const navigationLinks = [
  { to: '/', label: 'Dashboard' },
  { to: '/schedules', label: 'Schedules' },
  { to: '/results', label: 'Results' }
];

function LoginView() {
  const { login } = useAuth();

  return (
    <div className="flex min-h-screen items-center justify-center bg-slate-100 px-4">
      <div className="w-full max-w-md rounded-2xl bg-white p-8 shadow-xl">
        <span className="inline-flex rounded-full bg-blue-100 px-3 py-1 text-xs font-semibold uppercase tracking-wide text-blue-700">
          Secure access
        </span>
        <h1 className="mt-4 text-3xl font-bold text-slate-900">Web Crawler</h1>
        <p className="mt-3 text-sm text-slate-600">
          Sign in with Microsoft Entra ID to manage crawl jobs, schedules, and results through the API Gateway.
        </p>
        <button
          type="button"
          onClick={() => void login()}
          className="mt-6 w-full rounded-lg bg-blue-600 px-4 py-3 text-sm font-semibold text-white transition hover:bg-blue-700"
        >
          Sign in
        </button>
      </div>
    </div>
  );
}

function ProtectedLayout() {
  const { isAuthenticated, user, logout } = useAuth();

  if (!isAuthenticated) {
    return <Navigate to="/login" replace />;
  }

  return (
    <div className="min-h-screen bg-slate-100">
      <header className="border-b border-slate-200 bg-white shadow-sm">
        <div className="mx-auto flex max-w-7xl flex-wrap items-center justify-between gap-4 px-4 py-4 sm:px-6 lg:px-8">
          <div>
            <h1 className="text-xl font-semibold text-slate-900">Web Crawler</h1>
            <p className="text-sm text-slate-500">Monitor crawls, schedules, and content results in one place.</p>
          </div>

          <div className="flex items-center gap-3">
            <div className="flex items-center gap-3 rounded-2xl bg-slate-50 px-3 py-2">
              <div className="flex h-10 w-10 items-center justify-center rounded-full bg-blue-600 text-sm font-semibold text-white">
                {user?.initials ?? 'U'}
              </div>
              <div className="text-right">
                <p className="text-sm font-semibold text-slate-900">{user?.name ?? 'Signed in user'}</p>
                <p className="text-xs text-slate-500">{user?.username ?? ''}</p>
              </div>
            </div>
            <button
              type="button"
              onClick={() => void logout()}
              className="rounded-lg border border-slate-300 px-4 py-2 text-sm font-medium text-slate-700 transition hover:border-slate-400 hover:bg-slate-50"
            >
              Sign out
            </button>
          </div>
        </div>
        <nav className="mx-auto flex max-w-7xl flex-wrap gap-2 px-4 pb-4 sm:px-6 lg:px-8">
          {navigationLinks.map((link) => (
            <NavLink
              key={link.to}
              to={link.to}
              end={link.to === '/'}
              className={({ isActive }) =>
                `rounded-lg px-4 py-2 text-sm font-medium transition ${
                  isActive ? 'bg-blue-600 text-white' : 'text-slate-600 hover:bg-slate-100 hover:text-slate-900'
                }`
              }
            >
              {link.label}
            </NavLink>
          ))}
        </nav>
      </header>

      <main className="mx-auto max-w-7xl px-4 py-8 sm:px-6 lg:px-8">
        <Outlet />
      </main>
    </div>
  );
}

export default function App() {
  const { isAuthenticated } = useAuth();

  return (
    <Routes>
      <Route path="/login" element={isAuthenticated ? <Navigate to="/" replace /> : <LoginView />} />
      <Route element={<ProtectedLayout />}>
        <Route path="/" element={<JobDashboard />} />
        <Route path="/schedules" element={<ScheduleConfig />} />
        <Route path="/results" element={<CrawlResults />} />
      </Route>
      <Route path="*" element={<Navigate to={isAuthenticated ? '/' : '/login'} replace />} />
    </Routes>
  );
}
