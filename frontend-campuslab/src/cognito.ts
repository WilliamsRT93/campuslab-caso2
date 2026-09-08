import { cognitoConfig } from './authConfig';
import { createCodeChallenge, createCodeVerifier } from './pkce';

const VERIFIER_KEY = 'cognito_code_verifier';
const TOKEN_KEY = 'cognito_access_token';

interface CognitoTokenResponse {
  access_token: string;
  id_token?: string;
  token_type: string;
  expires_in: number;
  error_description?: string;
}

/**
 * Inicia el flujo Authorization Code con PKCE contra Cognito Hosted UI.
 * Si hay un proveedor federado configurado (Entra), salta directo al login de Microsoft.
 */
export async function loginWithCognito(): Promise<void> {
  const verifier = createCodeVerifier();
  const challenge = await createCodeChallenge(verifier);
  sessionStorage.setItem(VERIFIER_KEY, verifier);

  const params = new URLSearchParams({
    response_type: 'code',
    client_id: cognitoConfig.clientId,
    redirect_uri: cognitoConfig.redirectUri,
    scope: cognitoConfig.scopes.join(' '),
    code_challenge_method: 'S256',
    code_challenge: challenge,
  });
  if (cognitoConfig.identityProvider) {
    params.set('identity_provider', cognitoConfig.identityProvider);
  }
  window.location.assign(`${cognitoConfig.domain}/oauth2/authorize?${params.toString()}`);
}

/** Intercambia el authorization code por el access token de Cognito. */
export async function exchangeCodeForToken(code: string): Promise<string> {
  const verifier = sessionStorage.getItem(VERIFIER_KEY);
  if (!verifier) {
    throw new Error('No se encontro el verificador PKCE de Cognito.');
  }

  const response = await fetch(`${cognitoConfig.domain}/oauth2/token`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    body: new URLSearchParams({
      grant_type: 'authorization_code',
      client_id: cognitoConfig.clientId,
      code,
      redirect_uri: cognitoConfig.redirectUri,
      code_verifier: verifier,
    }),
  });

  const token = (await response.json()) as CognitoTokenResponse;
  sessionStorage.removeItem(VERIFIER_KEY);
  if (!response.ok) {
    throw new Error(token.error_description || 'No fue posible obtener el token de Cognito.');
  }
  sessionStorage.setItem(TOKEN_KEY, token.access_token);
  return token.access_token;
}

/** Cierra la sesion de Cognito en el hosted UI y limpia el token local. */
export function logoutFromCognito(): void {
  sessionStorage.removeItem(TOKEN_KEY);
  const params = new URLSearchParams({
    client_id: cognitoConfig.clientId,
    logout_uri: cognitoConfig.redirectUri.replace('/cognito-callback', ''),
  });
  window.location.assign(`${cognitoConfig.domain}/logout?${params.toString()}`);
}

export function getCognitoToken(): string | null {
  return sessionStorage.getItem(TOKEN_KEY);
}

export function clearCognitoToken(): void {
  sessionStorage.removeItem(TOKEN_KEY);
}
