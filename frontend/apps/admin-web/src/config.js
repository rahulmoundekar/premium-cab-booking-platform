const hostApi = import.meta.env.VITE_API_BASE_URL || 'http://localhost:58080/api/v1';
const isBackend = String(import.meta.env.VITE_BACKEND).toLowerCase() === 'true';
const isDemoMode = String(import.meta.env.VITE_DEMO_MODE).toLowerCase() === 'true';
const redirectUrl = typeof window !== 'undefined' ? window.location.origin : 'http://localhost:53001';

const appConfig = {
  hostApi,
  portApi: '',
  baseURLApi: hostApi,
  redirectUrl,
  isBackend,
  isDemoMode,
  auth: {
    email: import.meta.env.VITE_DEMO_EMAIL || 'admin@suabhi.local',
    password: import.meta.env.VITE_DEMO_PASSWORD || '',
  },
  app: { colors: { dark: '#002B49', light: '#FFFFFF', sea: '#004472', sky: '#E9EBEF', wave: '#D1E7F6', rain: '#CCDDE9', middle: '#D7DFE6', black: '#13191D', salat: '#21AE8C' } },
};

export default appConfig;
