const protocol = globalThis.location?.protocol === 'https:' ? 'wss' : 'ws';
const host = globalThis.location?.host ?? 'localhost';

export const environment = {
  apiUrl: '/api/v1',
  wsUrl: `${protocol}://${host}/ws`,
};
