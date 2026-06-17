import { ReactNode, useEffect, useState } from 'react';
import { AuthenticationResult, EventType } from '@azure/msal-browser';
import { MsalProvider } from '@azure/msal-react';
import { msalInstance } from './authConfig';

interface AuthProviderProps {
  children: ReactNode;
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
        <div className="rounded-xl bg-white px-6 py-4 shadow-sm">
          <p className="text-sm font-medium text-slate-600">Loading authentication...</p>
        </div>
      </div>
    );
  }

  return <MsalProvider instance={msalInstance}>{children}</MsalProvider>;
}
