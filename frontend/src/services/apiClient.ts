import axios from 'axios';
import { apiError } from './apiError';

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
      // Отказ логируется ВСЕГДА, а не только в dev-сборке. Раньше здесь стоял тот же
      // `import.meta.env.DEV`, что и у болтливых логов запроса/ответа, — и в проде у сбоя не
      // оставалось никакого следа: ни в консоли, ни где-либо ещё. Строка короткая (метод, адрес,
      // статус, код и текст отказа), тело ответа целиком по-прежнему печатается только в dev.
      const e = apiError(error);
      const where = `${error.config?.method?.toUpperCase() ?? '?'} ${error.config?.url ?? '?'}`;
      console.error(`❌ Ошибка запроса: ${where} → ${e.status ?? 'нет ответа'}${e.code ? ` ${e.code}` : ''}: ${e.message}`
          + (e.correlationId ? ` [${e.correlationId}]` : ''));

      if (import.meta.env.DEV) {
        console.error('   тело ответа:', error.response?.data ?? error.message);
      }
      return Promise.reject(error);
    }
);

export default api;
