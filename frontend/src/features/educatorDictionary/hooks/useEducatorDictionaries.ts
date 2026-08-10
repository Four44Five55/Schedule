import { useCallback, useEffect, useState } from 'react';
import { EducatorDictionaryService, type EducatorDictionaryKind } from '../../../services/apiServices';
import type { DictionaryEntryDto } from '../../../types/api';

/**
 * Загрузка одного справочника регалий (звания / роды службы / отрасли науки).
 *
 * <p>Один хук на все виды — вид передаётся параметром. Три копии загрузки отличались бы только
 * строкой пути, а будущая должность подключится сюда же значением {@code kind}, без нового кода
 * (тот же приём, что у {@code useOrgUnits}: один источник на всех потребителей).</p>
 */
export function useEducatorDictionary(kind: EducatorDictionaryKind) {
    const [entries, setEntries] = useState<DictionaryEntryDto[]>([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState<string | null>(null);

    const reload = useCallback(async () => {
        setLoading(true);
        setError(null);
        try {
            setEntries(await EducatorDictionaryService.getAll(kind));
        } catch (e) {
            console.error('Ошибка загрузки справочника', kind, e);
            // Ошибку не проглатываем показом пустого списка: пустой справочник читался бы как
            // «званий нет», и пользователь начал бы заводить дубли уже существующих.
            setError('Не удалось загрузить справочник');
        } finally {
            setLoading(false);
        }
    }, [kind]);

    useEffect(() => {
        void reload();
    }, [reload]);

    return { entries, loading, error, reload };
}

/**
 * Все три справочника разом — для формы преподавателя, где нужны и звания, и службы, и отрасли.
 *
 * <p>Каждый справочник грузится своим запросом: их десятки строк, экономить тут нечего, а общий
 * эндпоинт «отдай всё» пришлось бы держать в актуальном состоянии при добавлении четвёртого.</p>
 */
export function useEducatorCredentialDictionaries() {
    const ranks = useEducatorDictionary('special-ranks');
    const services = useEducatorDictionary('rank-services');
    const branches = useEducatorDictionary('science-branches');

    return {
        ranks: ranks.entries,
        services: services.entries,
        branches: branches.entries,
        loading: ranks.loading || services.loading || branches.loading,
        error: ranks.error ?? services.error ?? branches.error,
    };
}

/**
 * Значения для выпадающего списка: активные плюс уже выбранное.
 *
 * <p>Погашенное значение не предлагается, но если оно стоит в карточке — показывается, иначе
 * правка карточки молча стёрла бы звание (то же правило, что у расформированных подразделений).</p>
 */
export function selectableEntries(
    entries: DictionaryEntryDto[],
    selectedId: number | null | undefined,
): DictionaryEntryDto[] {
    return entries.filter((e) => e.active || e.id === selectedId);
}
