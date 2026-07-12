/**
 * Запускает скачивание бинарного ответа (Blob) браузером через временный object-URL.
 * Используется для файлов, которые бэк отдаёт как вложение (например, выгрузка расписания в Excel).
 */
export function downloadBlob(blob: Blob, filename: string): void {
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = filename;
  document.body.appendChild(a);
  a.click();
  a.remove();
  URL.revokeObjectURL(url);
}

/**
 * Достаёт имя файла из заголовка Content-Disposition.
 * Поддерживает RFC 5987 (`filename*=UTF-8''%D0%A0...`, кириллица) и обычный `filename="..."`.
 * Возвращает null, если заголовок пуст или имя не распознано (вызывающий подставит дефолт).
 */
export function filenameFromContentDisposition(header?: string | null): string | null {
  if (!header) return null;
  const star = /filename\*=UTF-8''([^;]+)/i.exec(header);
  if (star) {
    try { return decodeURIComponent(star[1]); } catch { /* fallthrough */ }
  }
  const plain = /filename="?([^"]+)"?/i.exec(header);
  return plain ? plain[1] : null;
}
