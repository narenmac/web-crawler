import { NavLink, Route, Routes } from 'react-router-dom';
import { useIsAuthenticated, useMsal } from '@azure/msal-react';
import JobDashboard from './components/JobDashboard';
import ScheduleConfig from './components/ScheduleConfig';
import CrawlResults from './components/CrawlResults';
import { loginRequest } from './auth/authConfig';

const navigationLinks = [
  { to: '/', label: 'Dashboard' },
  { to: '/schedules', label: 'Schedules' },
  { to: '/results', label: 'Results' }
];

function LoginView() {
  const { instance } = useMsal();

  const handleLogin = () => {
    void instance.loginRedirect(loginRequest);
  };

  return (
    <div className="flex min-h-screen items-center justify-center bg-slate-100 px-4">
      <div className="w-full max-w-md rounded-2xl bg-white p-8 shadow-xl">
        <span className="inline-flex rounded-full bg-blue-100 px-3 py-1 text-xs font-semibold uppercase tracking-wide text-blue-700">
          Secure access
        </span>
        <h1 className="mt-4 text-3xl font-bold text-slate-900">Web Crawler</h1>
        <p className="mt-3 text-sm text-slate-600">
          Sign in with Microsoft Entra ID to manage crawling jobs, schedules, and results.
        </p>
        <button
          type="button"
          onClick={handleLogin}
          className="mt-6 w-full rounded-lg bg-blue-600 px-4 py-3 text-sm font-semibold text-white transition hover:bg-blue-700"
        >
          Sign in
        </button>
      </div>
    </div>
  );
}

function AppShell() {
  const { accounts, instance } = useMsal();
  const activeAccount = instance.getActiveAccount() ?? accounts[0];

  const handleLogout = () => {
    void instance.logoutRedirect({
      account: activeAccount
    });
  };

  return (
    <div className="min-h-screen bg-slate-100">
      <header className="border-b border-slate-200 bg-white shadow-sm">
        <div className="mx-auto flex max-w-7xl items-center justify-between px-4 py-4 sm:px-6 lg:px-8">
          <div>
            <h1 className="text-xl font-semibold text-slate-900">Web Crawler</h1>
            <p className="text-sm text-slate-500">
              Welcome back{activeAccount?.name ? `, ${activeAccount.name}` : ''}.
            </p>
          </div>
          <button
            type="button"
            onClick={handleLogout}
            className="rounded-lg border border-slate-300 px-4 py-2 text-sm font-medium text-slate-700 transition hover:border-slate-400 hover:bg-slate-50"
          >
            Sign out
          </button>
        </div>
        <nav className="mx-auto flex max-w-7xl gap-2 px-4 pb-4 sm:px-6 lg:px-8">
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
        <Routes>
          <Route path="/" element={<JobDashboard />} />
          <Route path="/schedules" element={<ScheduleConfig />} />
          <Route path="/results" element={<CrawlResults />} />
        </Routes>
      </main>
    </div>
  );
}

export default function App() {
  const isAuthenticated = useIsAuthenticated();

  return isAuthenticated ? <AppShell /> : <LoginView />;
}
