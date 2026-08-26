import React, { createContext, useContext, useState, useEffect, useMemo, useCallback } from 'react';
import { EnumService, ConstraintKindService } from '../services/apiServices';
import type { ConstraintKindDto, EnumDto } from '../types/api';
import { DEFAULT_KIND_CATEGORY, KindCategory } from '../features/schedule/kindStyles';
import { ConstraintStyle, constraintStyleOfColor } from '../features/constraints/constraintStyles';
import { useToast } from './ToastContext';

/**
 * Данные всех enum-ов, загруженные с бэкенда.
 * Единый источник правды — никакого хардкода.
 */
interface EnumData {
    kindOfStudy: EnumDto[];
    daysOfWeek: EnumDto[];
    timeSlots: EnumDto[];
    /**
     * Виды ограничений — уже НЕ enum, а пользовательский справочник (`/api/constraint-kinds`).
     * Живут здесь же, потому что нужны тем же экранам и грузятся тем же разом при старте;
     * тип другой (`ConstraintKindDto`), и это намеренно: у справочника есть цвет, активность
     * и число использований, которых у enum-значения не бывает.
     */
    constraintKinds: ConstraintKindDto[];
    periodTypes: EnumDto[];
    orgUnitTypes: EnumDto[];
    /** Уровни учёной степени (кандидат/доктор). Отрасль науки — справочник, не enum. */
    academicDegrees: EnumDto[];
    /** Учёные звания (доцент/профессор) — не должности. */
    academicTitles: EnumDto[];
    loading: boolean;
}

/**
 * Вспомогательные функции для быстрого доступа к лейблам.
 */
interface EnumHelpers {
    /** Получить полное название по value (например, "MONDAY" → "Понедельник") */
    getDayLabel: (value: string) => string;
    /** Получить сокращение по value (например, "MONDAY" → "Пн") */
    getDayShort: (value: string) => string;
    /** Получить полное название слота (например, "FIRST" → "1-я пара") */
    getSlotLabel: (value: string) => string;
    /** Получить сокращение слота (например, "FIRST" → "1") */
    getSlotShort: (value: string) => string;
    /** Получить доп. инфо слота — время (например, "FIRST" → "09:00 – 10:35") */
    getSlotTime: (value: string) => string;
    /** Получить название вида занятия */
    getStudyLabel: (value: string) => string;
    /** Получить сокращение вида занятия */
    getStudyShort: (value: string) => string;
    /**
     * Категория вида занятия (LECTURE / PRACTICE / PROGRESS_CHECK / ASSESSMENT) — приходит с бэка.
     *
     * Единственный способ узнать «это аттестация?» на фронте: списки видов здесь не заводим,
     * классификацией владеет Java-enum `KindOfStudy.Category`. Пока enum-ы грузятся, отдаёт
     * `DEFAULT_KIND_CATEGORY` — нейтральную категорию, а не пустое значение.
     */
    getStudyCategory: (value?: string | null) => KindCategory;
    /** Получить название вида ограничения по коду */
    getConstraintLabel: (value: string) => string;
    /** Стиль вида ограничения по коду: цвет берётся из справочника, классы — из палитры фронта. */
    getConstraintStyle: (value?: string | null) => ConstraintStyle;
    /** Перечитать справочник видов ограничений — после правок в его редакторе. */
    reloadConstraintKinds: () => Promise<void>;
    /** Получить название вида подразделения (например, "DEPARTMENT" → "Кафедра") */
    getOrgUnitTypeLabel: (value: string) => string;
    /** Получить сокращение вида подразделения (например, "DEPARTMENT" → "Каф.") */
    getOrgUnitTypeShort: (value: string) => string;
    /** Название уровня степени (например, "CANDIDATE" → "кандидат наук") */
    getAcademicDegreeLabel: (value: string) => string;
    /** Название учёного звания (например, "ASSOCIATE_PROFESSOR" → "доцент") */
    getAcademicTitleLabel: (value: string) => string;
}

type EnumContextType = EnumData & EnumHelpers;

const EnumContext = createContext<EnumContextType | null>(null);

/**
 * Провайдер enum-ов. Оборачивает приложение, загружает enum-ы один раз при старте.
 */
export const EnumProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
    const toast = useToast();
    const [data, setData] = useState<EnumData>({
        kindOfStudy: [],
        daysOfWeek: [],
        timeSlots: [],
        constraintKinds: [],
        periodTypes: [],
        orgUnitTypes: [],
        academicDegrees: [],
        academicTitles: [],
        loading: true,
    });

    // Справочник видов ограничений перечитывается отдельно от enum-ов: его правит пользователь,
    // и после правки список должен обновиться без перезагрузки страницы.
    const loadConstraintKinds = useCallback(async () => {
        try {
            const kinds = await ConstraintKindService.getAll();
            setData((prev) => ({ ...prev, constraintKinds: kinds }));
        } catch (err) {
            // Молчать нельзя: справочник перечитывают ПОСЛЕ правки, и невидимый отказ выглядит
            // как «правка не сохранилась» — человек идёт править второй раз.
            toast.failure(err, 'Виды ограничений не перечитались — список на экране устарел.');
        }
    }, [toast]);

    useEffect(() => {
        // Два источника: enum-ы (значения из кода) и справочник видов ограничений (данные
        // пользователя). Ждём оба, но падение справочника не должно оставить приложение
        // в вечной загрузке — отсюда allSettled вместо all.
        Promise.allSettled([EnumService.getAll(), ConstraintKindService.getAll()])
            .then(([enumsResult, kindsResult]) => {
                // Без перечней подписи вырождаются в коды («LECTURE» вместо «Лекция»), а выбор вида
                // в формах становится пустым. Экран при этом рисуется — то есть выглядит рабочим,
                // и без сообщения человек ищет причину в данных.
                if (enumsResult.status === 'rejected') {
                    toast.failure(enumsResult.reason,
                        'Не удалось загрузить перечни — подписи и списки выбора будут неполными.');
                }
                if (kindsResult.status === 'rejected') {
                    toast.failure(kindsResult.reason,
                        'Не удалось загрузить виды ограничений — разметка ограничений будет без подписей.');
                }
                const enums = enumsResult.status === 'fulfilled' ? enumsResult.value : null;
                setData({
                    kindOfStudy: enums?.kindOfStudy ?? [],
                    daysOfWeek: enums?.daysOfWeek ?? [],
                    timeSlots: enums?.timeSlots ?? [],
                    constraintKinds: kindsResult.status === 'fulfilled' ? kindsResult.value : [],
                    periodTypes: enums?.periodTypes ?? [],
                    orgUnitTypes: enums?.orgUnitTypes ?? [],
                    academicDegrees: enums?.academicDegrees ?? [],
                    academicTitles: enums?.academicTitles ?? [],
                    loading: false,
                });
            });
    }, [toast]);

    // Хелперы мемоизируем — пересчитываются только при изменении данных
    const helpers = useMemo<EnumHelpers>(() => {
        const findInList = (list: EnumDto[], value: string) =>
            list.find((e) => e.value === value);

        return {
            getDayLabel: (v) => findInList(data.daysOfWeek, v)?.label ?? v,
            getDayShort: (v) => findInList(data.daysOfWeek, v)?.abbreviation ?? v,
            getSlotLabel: (v) => findInList(data.timeSlots, v)?.label ?? v,
            getSlotShort: (v) => findInList(data.timeSlots, v)?.abbreviation ?? v,
            getSlotTime: (v) => findInList(data.timeSlots, v)?.extra ?? '',
            getStudyLabel: (v) => findInList(data.kindOfStudy, v)?.label ?? v,
            getStudyShort: (v) => findInList(data.kindOfStudy, v)?.abbreviation ?? v,
            getStudyCategory: (v) =>
                (v ? (findInList(data.kindOfStudy, v)?.category as KindCategory | undefined) : undefined)
                ?? DEFAULT_KIND_CATEGORY,
            getConstraintLabel: (v) =>
                data.constraintKinds.find((k) => k.code === v)?.name ?? v,
            getConstraintStyle: (v) =>
                constraintStyleOfColor(v ? data.constraintKinds.find((k) => k.code === v)?.color : null),
            reloadConstraintKinds: loadConstraintKinds,
            getOrgUnitTypeLabel: (v) => findInList(data.orgUnitTypes, v)?.label ?? v,
            getOrgUnitTypeShort: (v) => findInList(data.orgUnitTypes, v)?.abbreviation ?? v,
            getAcademicDegreeLabel: (v) => findInList(data.academicDegrees, v)?.label ?? v,
            getAcademicTitleLabel: (v) => findInList(data.academicTitles, v)?.label ?? v,
        };
    }, [data.daysOfWeek, data.timeSlots, data.kindOfStudy, data.constraintKinds, data.orgUnitTypes,
        data.academicDegrees, data.academicTitles, loadConstraintKinds]);

    const value = useMemo(() => ({ ...data, ...helpers }), [data, helpers]);

    return <EnumContext.Provider value={value}>{children}</EnumContext.Provider>;
};

/**
 * Хук для доступа к enum-ам из любого компонента.
 *
 * Использование:
 * ```
 * const { daysOfWeek, getDayShort, getSlotLabel } = useEnums();
 * ```
 */
export const useEnums = (): EnumContextType => {
    const ctx = useContext(EnumContext);
    if (!ctx) {
        throw new Error('useEnums() must be used inside <EnumProvider>');
    }
    return ctx;
};
