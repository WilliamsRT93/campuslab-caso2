import type { Configuration } from '@azure/msal-browser';
import { LogLevel } from '@azure/msal-browser';

/**
 * Configuracion de MSAL: identifica al usuario contra Microsoft Entra ID.
 * Este es el "autenticador de Microsoft" que abre la sesion en el frontend.
 */
export const msalConfig: Configuration = {
  auth: {
    clientId: import.meta.env.VITE_AZURE_CLIENT_ID,
    authority: `https://login.microsoftonline.com/${import.meta.env.VITE_AZURE_TENANT_ID}`,
    redirectUri: import.meta.env.VITE_AZURE_REDIRECT_URI,
  },
  cache: {
    cacheLocation: 'localStorage',
  },
  system: {
    loggerOptions: {
      loggerCallback: (level, message, containsPii) => {
        if (containsPii) return;
        if (level === LogLevel.Error) console.error(message);
      },
      logLevel: LogLevel.Error,
    },
  },
};

/** Scopes minimos para la sesion de usuario. */
export const loginRequest = {
  scopes: ['User.Read'],
};

/**
 * Configuracion de Amazon Cognito (Hosted UI, App Client publico con PKCE).
 * Cognito esta federado con Entra ID, por lo que el login se delega a Microsoft
 * y Cognito emite el access token que valida el API Gateway y el BFF.
 */
export const cognitoConfig = {
  domain: import.meta.env.VITE_COGNITO_DOMAIN,
  clientId: import.meta.env.VITE_COGNITO_CLIENT_ID,
  redirectUri: import.meta.env.VITE_COGNITO_REDIRECT_URI,
  identityProvider: import.meta.env.VITE_COGNITO_IDP,
  scopes: ['openid', 'email', 'profile'],
};

/** URL base del API Gateway que enruta hacia el BFF. */
export const apiBaseUrl = import.meta.env.VITE_API_BASE_URL;
