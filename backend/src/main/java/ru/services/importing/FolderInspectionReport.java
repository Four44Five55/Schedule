package ru.services.importing;

import ru.services.importing.ParsedSheet.CutKind;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Итог разбора целого каталога выгрузки. <b>Сводка по пачке, а не по каждому файлу.</b>
 *
 * <p>Причина в масштабе: в живой выгрузке 1792 файла на 390 МБ. Отчёт «карточка на файл» весил бы
 * десятки мегабайт JSON и повесил бы браузер, а прочитать его всё равно нельзя — полторы тысячи
 * карточек никто не просматривает. Поэтому здесь цифры по пачке, а подробности — по одному файлу
 * через обычную загрузку.</p>
 *
 * <p><b>Замечания схлопнуты по тексту.</b> Одинаковая кривизна в трёхстах файлах — это одна находка
 * с числом 300, а не триста строк: список из трёхсот одинаковых строк прячет остальные находки.</p>
 *
 * @param path          каталог, который читали
 * @param files         сколько файлов найдено
 * @param parsed        сколько разобрано (шапка опознана)
 * @param failed        сколько не удалось прочитать вовсе
 * @param byCut         сколько файлов каждого разреза — сразу видно, весь ли комплект на месте
 * @param lessons       всего занятий во всех файлах
 * @param markers       всего маркеров занятости
 * @param firstDate     самая ранняя восстановленная дата
 * @param lastDate      самая поздняя
 * @param problems      замечания с числом повторов, самые частые первыми
 * @param problemsTotal сколько замечаний всего (до схлопывания)
 * @param matching      сверка со справочниками по всей пачке
 */
public record FolderInspectionReport(
        String path,
        int files,
        int parsed,
        int failed,
        Map<CutKind, Integer> byCut,
        int lessons,
        int markers,
        LocalDate firstDate,
        LocalDate lastDate,
        List<ProblemCount> problems,
        int problemsTotal,
        ImportMatchReport matching
) {

    /**
     * @param message текст замечания
     * @param count   в скольких файлах встретился
     * @param example имя одного из файлов, где он встретился — с него начинают разбираться.
     *                Без него замечание в прогоне на 1792 файла нечем проверить: текст описывает
     *                ячейку («Вт 1 колонка 5»), но не говорит, в каком файле её искать
     */
    public record ProblemCount(String message, int count, String example) {
    }
}
