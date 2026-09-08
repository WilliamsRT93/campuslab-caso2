import { useEffect, useState } from 'react';
import { useMsal, useIsAuthenticated } from '@azure/msal-react';
import { InteractionStatus } from '@azure/msal-browser';
import { loginRequest } from './authConfig';
import { api } from './api';
import type { Identidad } from './api';
import {
  clearCognitoToken,
  exchangeCodeForToken,
  getCognitoToken,
  loginWithCognito,
  logoutFromCognito,
} from './cognito';
import { ProductosPanel } from './components/ProductosPanel';
import { ReservasPanel } from './components/ReservasPanel';
import './App.css';

type Tab = 'productos' | 'reservas';

export default function App() {
  const { instance, accounts, inProgress } = useMsal();
  const isAuthenticated = useIsAuthenticated();
  const cuenta = accounts[0];

  const [cognitoToken, setCognitoToken] = useState<string | null>(getCognitoToken());
  const [identidad, setIdentidad] = useState<Identidad | null>(null);
  const [tab, setTab] = useState<Tab>('productos');
  const [error, setError] = useState<string | null>(null);

  // Procesa el retorno de Cognito (Authorization Code + PKCE) en /cognito-callback.
  useEffect(() => {
    const params = new URLSearchParams(window.location.search);
    const code = params.get('code');
    const err = params.get('error_description') || params.get('error');
    if (window.location.pathname !== '/cognito-callback') return;

    if (err) {
      setError(`Cognito rechazo el login: ${err}`);
      window.history.replaceState({}, document.title, '/');
      return;
    }
    if (code) {
      exchangeCodeForToken(code)
        .then((token) => {
          setCognitoToken(token);
          window.history.replaceState({}, document.title, '/');
        })
        .catch((e: Error) => {
          setError(e.message);
          window.history.replaceState({}, document.title, '/');
        });
    }
  }, []);

  // Carga la identidad/roles desde el BFF cuando ya hay token de Cognito.
  useEffect(() => {
    if (!cognitoToken) return;
    api
      .me()
      .then(setIdentidad)
      .catch((e: Error) => setError(e.message));
  }, [cognitoToken]);

  const loginMicrosoft = () => {
    if (inProgress === InteractionStatus.None) {
      instance.loginRedirect(loginRequest).catch((e) => console.error(e));
    }
  };

  const logoutTodo = () => {
    clearCognitoToken();
    if (getCognitoToken() === null) {
      // Cierra tambien la sesion de Microsoft.
      instance.logoutRedirect({ postLogoutRedirectUri: '/' }).catch((e) => console.error(e));
    }
  };

  const cerrarCognito = () => logoutFromCognito();

  // --- Pantalla 1: login con Microsoft (MSAL) ---
  if (!isAuthenticated) {
    return (
      <main className="shell">
        <div className="card login-card">
          <p className="kicker">CampusLab · Cloud Native</p>
          <h1>Reserva de laboratorios y equipos</h1>
          <p className="intro">
            Inicia sesion con tu cuenta institucional de Microsoft para acceder a la plataforma.
          </p>
          <button className="btn" onClick={loginMicrosoft} disabled={inProgress !== InteractionStatus.None}>
            {inProgress !== InteractionStatus.None ? 'Iniciando...' : 'Iniciar sesion con Microsoft'}
          </button>
        </div>
      </main>
    );
  }

  // --- Pantalla 2: obtener token de la API via Cognito (federado a Microsoft) ---
  if (!cognitoToken) {
    return (
      <main className="shell">
        <div className="card login-card">
          <p className="kicker">Paso 2 · Autorizacion de la API</p>
          <h1>Conectar con la API</h1>
          <p className="intro">
            Sesion de Microsoft activa como <strong>{cuenta?.name || cuenta?.username}</strong>.
            Ahora obtendremos un token de Amazon Cognito (federado con Entra ID) para consumir el
            API Gateway.
          </p>
          <button className="btn" onClick={() => void loginWithCognito()}>
            Autorizar y entrar a CampusLab
          </button>
          <button className="btn btn-ghost" onClick={logoutTodo}>
            Cerrar sesion de Microsoft
          </button>
          {error && <p className="error">{error}</p>}
        </div>
      </main>
    );
  }

  // --- Pantalla 3: aplicacion con CRUD ---
  return (
    <main className="shell">
      <header className="topbar">
        <div>
          <p className="kicker">CampusLab</p>
          <h1>Consola de operaciones</h1>
        </div>
        <div className="user-box">
          <div className="user-meta">
            <strong>{identidad?.username || cuenta?.name}</strong>
            <span className={`role-tag ${identidad?.isAdmin ? 'is-admin' : ''}`}>
              {identidad?.isAdmin ? 'Admin' : (identidad?.roles?.[0]?.replace('ROLE_', '') || 'Usuario')}
            </span>
          </div>
          <button className="btn btn-ghost" onClick={cerrarCognito}>Cerrar sesion</button>
        </div>
      </header>

      <nav className="tabs">
        <button className={tab === 'productos' ? 'active' : ''} onClick={() => setTab('productos')}>
          Catalogo (productos)
        </button>
        <button className={tab === 'reservas' ? 'active' : ''} onClick={() => setTab('reservas')}>
          Reservas
        </button>
      </nav>

      {error && <p className="error">{error}</p>}

      {tab === 'productos'
        ? <ProductosPanel isAdmin={identidad?.isAdmin ?? false} />
        : <ReservasPanel isAdmin={identidad?.isAdmin ?? false} />}
    </main>
  );
}
