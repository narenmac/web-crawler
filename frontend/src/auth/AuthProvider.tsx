import { ReactNode, createContext, useContext, useEffect, useMemo, useState } from 'react';
import { AccountInfo, AuthenticationResult, EventType } from '@azure/msal-browser';
import { MsalProvider, useIsAuthenticated, useMsal } from '@azure/msal-react';
import { AuthUser } from '../types';
import { loginRequest, msalInstance } from './authConfig';

interface AuthProviderProps {
  children: ReactNode;
}

interface AuthContextValue {
  isAuthenticated: boolean;
  user: AuthUser | null;
  login: () => Promise<void>;
  logout: () => Promise<void>;
  getToken: () => Promise<string | null>;
}

const AuthContext = createContext<AuthContextValue | undefined>(undefined);

const toUser = (account: AccountInfo | null | undefined): AuthUser | null => {
  if (!account) {
    return null;
  }

  const fullName = account.name?.trim() || account.username || 'User';
  const initials = fullName
    .split(/\s+/)
    .slice(0, 2)
    .map((part) => part[0]?.toUpperCase() ?? '')
    .join('');

  return {
    name: fullName,
    username: account.username,
    initials: initials || 'U'
  };
};

function AuthContextProvider({ children }: AuthProviderProps) {
  const { instance, accounts } = useMsal();
  const isAuthenticated = useIsAuthenticated();
  const activeAccount = instance.getActiveAccount() ?? accounts[0] ?? null;

  useEffect(() => {
    if (activeAccount && activeAccount !== instance.getActiveAccount()) {
      instance.setActiveAccount(activeAccount);
    }
  }, [activeAccount, instance]);

  const value = useMemo<AuthContextValue>(
    () => ({
      isAuthenticated,
      user: toUser(activeAccount),
      login: async () => {
        await instance.loginRedirect(loginRequest);
      },
      logout: async () => {
        await instance.logoutRedirect({ account: activeAccount ?? undefined });
      },
      getToken: async () => {
        if (!activeAccount) {
          return null;
        }

        const token = await instance.acquireTokenSilent({
          ...loginRequest,
          account: activeAccount
        });

        return token.accessToken;
      }
    }),
    [activeAccount, instance, isAuthenticated]
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth() {
  const context = useContext(AuthContext);

  if (!context) {
    throw new Error('useAuth must be used within AuthProvider');
  }

  return context;
}

export default function AuthProvider({ children }: AuthProviderProps) {
  const [ready, setReady] = useState(false);

  useEffect(() => {
    let mounted = true;
    let callbackId: string | null = null;

    const initializeMsal = async () => {
      await msalInstance.initialize();

      const response = await msalInstance.handleRedirectPromise();
      const account = response?.account ?? msalInstance.getActiveAccount() ?? msalInstance.getAllAccounts()[0];

      if (account) {
        msalInstance.setActiveAccount(account);
      }

      callbackId = msalInstance.addEventCallback((event) => {
        if (event.eventType === EventType.LOGIN_SUCCESS && event.payload) {
          const authResult = event.payload as AuthenticationResult;
          msalInstance.setActiveAccount(authResult.account);
        }
      });

      if (mounted) {
        setReady(true);
      }
    };

    void initializeMsal().catch(() => {
      if (mounted) {
        setReady(true);
      }
    });

    return () => {
      mounted = false;

      if (callbackId) {
        msalInstance.removeEventCallback(callbackId);
      }
    };
  }, []);

  if (!ready) {
    return (
      <div className="flex min-h-screen items-center justify-center bg-slate-100">
        <div className="rounded-2xl bg-white px-6 py-4 shadow-lg">
          <p className="text-sm font-medium text-slate-600">Loading authentication...</p>
        </div>
      </div>
    );
  }

  return (
    <MsalProvider instance={msalInstance}>
      <AuthContextProvider>{children}</AuthContextProvider>
    </MsalProvider>
  );
}
