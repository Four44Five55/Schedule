import axios from 'axios';

const api = axios.create({
  baseURL: '/api',
  headers: {
    'Content-Type': 'application/json',
  },
});

// Логгирование для отладки — только в dev-режиме (в прод-сборке вырезается)
if (import.meta.env.DEV) {
  api.interceptors.request.use((config) => {
    console.log(`🚀 Запрос: ${config.method?.toUpperCase()} ${config.baseURL}${config.url}`, config.data ?? '');
    return config;
  });
}

api.interceptors.response.use(
    (response) => {
      if (import.meta.env.DEV) {
        console.log(`✅ Ответ от ${response.config.url}:`, response.data);
      }
      return response;
    },
    (error) => {
      if (import.meta.env.DEV) {
        console.error('❌ Ошибка запроса:', error.config?.method?.toUpperCase(), error.config?.url, error.response?.status, error.response?.data || error.message);
      }
      return Promise.reject(error);
    }
);

export default api;
