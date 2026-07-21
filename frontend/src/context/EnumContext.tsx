import React, { createContext, useContext, useState, useEffect, useMemo } from 'react';
import { EnumService } from '../services/apiServices';
import type { EnumDto } from '../types/api';

/**
 * Данные всех enum-ов, загруженные с бэкенда.
 * Единый источник правды — никакого хардкода.
 */
interface EnumData {
    kindOfStudy: EnumDto[];
    daysOfWeek: EnumDto[];
    timeSlots: EnumDto[];
    kindOfConstraints: EnumDto[];
    periodTypes: EnumDto[];
    orgUnitTypes: EnumDto[];
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
    /** Получить название ограничения */
    getConstraintLabel: (value: string) => string;
    /** Получить название вида подразделения (например, "DEPARTMENT" → "Кафедра") */
    getOrgUnitTypeLabel: (value: string) => string;
    /** Получить сокращение вида подразделения (например, "DEPARTMENT" → "Каф.") */
    getOrgUnitTypeShort: (value: string) => string;
}

type EnumContextType = EnumData & EnumHelpers;

const EnumContext = createContext<EnumContextType | null>(null);

/**
 * Провайдер enum-ов. Оборачивает приложение, загружает enum-ы один раз при старте.
 */
export const EnumProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
    const [data, setData] = useState<EnumData>({
        kindOfStudy: [],
        daysOfWeek: [],
        timeSlots: [],
        kindOfConstraints: [],
        periodTypes: [],
        orgUnitTypes: [],
        loading: true,
    });

    useEffect(() => {
        EnumService.getAll()
            .then((enums) => {
                setData({
                    kindOfStudy: enums.kindOfStudy,
                    daysOfWeek: enums.daysOfWeek,
                    timeSlots: enums.timeSlots,
                    kindOfConstraints: enums.kindOfConstraints,
                    periodTypes: enums.periodTypes,
                    orgUnitTypes: enums.orgUnitTypes ?? [],
                    loading: false,
                });
            })
            .catch((err) => {
                console.error('Ошибка загрузки enum-ов:', err);
                setData((prev) => ({ ...prev, loading: false }));
            });
    }, []);

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
            getConstraintLabel: (v) => findInList(data.kindOfConstraints, v)?.label ?? v,
            getOrgUnitTypeLabel: (v) => findInList(data.orgUnitTypes, v)?.label ?? v,
            getOrgUnitTypeShort: (v) => findInList(data.orgUnitTypes, v)?.abbreviation ?? v,
        };
    }, [data.daysOfWeek, data.timeSlots, data.kindOfStudy, data.kindOfConstraints, data.orgUnitTypes]);

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
