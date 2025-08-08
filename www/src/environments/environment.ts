
export const environment = {
  production: false,
  // @ts-ignore
  envName: window['env']?.['envName'] || 'dev',
  // @ts-ignore
  apiBaseUrl: window['env']?.['apiBaseUrl'] || 'http://localhost:8080',
  // @ts-ignore
  version: window['env']?.['version'] || 'http://localhost:8080'
};
