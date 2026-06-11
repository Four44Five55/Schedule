import axios from 'axios';

const api = axios.create({
  // Укажите адрес вашего запущенного Spring Boot приложения
  baseURL: 'http://localhost:8080/api',
  headers: {
    'Content-Type': 'application/json',
  },
  // Включаем передачу кук, если планируется авторизация
  withCredentials: true,
});

// Добавим логгирование для отладки реальных запросов
api.interceptors.request.use((config) => {
  console.log(`🚀 Запрос: ${config.method?.toUpperCase()} ${config.url}`);
  return config;
});

api.interceptors.response.use(
  (response) => {
    console.log(`✅ Ответ от ${response.config.url}:`, response.data);
    return response;
  },
  (error) => {
    console.error('❌ Ошибка запроса:', error.response?.data || error.message);
    return Promise.reject(error);
  }
);

export default api;
