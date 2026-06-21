import { PublicClientApplication } from '@azure/msal-browser';

const AUTH_DISABLED = process.env.REACT_APP_AUTH_DISABLED === 'true';

const tenantId = process.env.REACT_APP_AZURE_TENANT_ID ?? 'common';
const clientId = process.env.REACT_APP_AZURE_CLIENT_ID ?? '00000000-0000-0000-0000-000000000000';
const redirectUri = process.env.REACT_APP_AZURE_REDIRECT_URI ?? window.location.origin;
const configuredScopes = (process.env.REACT_APP_AZURE_SCOPES ?? 'User.Read')
  .split(/[,\s]+/)
  .map((scope) => scope.trim())
  .filter(Boolean);

export const msalConfig = {
  auth: {
    clientId,
    authority: `https://login.microsoftonline.com/${tenantId}`,
    redirectUri,
    navigateToLoginRequestUrl: false
  },
  cache: {
    cacheLocation: 'sessionStorage' as const,
    storeAuthStateInCookie: false
  }
};

export const loginRequest = {
  scopes: configuredScopes.length > 0 ? configuredScopes : ['User.Read']
};

// Only create MSAL instance when auth is NOT disabled — prevents unwanted redirects in local dev
export const msalInstance = AUTH_DISABLED
  ? (null as unknown as PublicClientApplication)
  : new PublicClientApplication(msalConfig);
