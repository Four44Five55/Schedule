import { useCallback, useEffect, useMemo, useState } from 'react';
import { ResourceService } from '../../../services/apiServices';
import type { OrgUnitDto } from '../../../types/api';

/**
 * Узел дерева подразделений: та же запись плюс уже разобранные дети и глубина.
 */
export interface OrgUnitNode extends OrgUnitDto {
    children: OrgUnitNode[];
    /** 0 — верхний уровень. Нужна для отступов в дереве и в выпадающих списках. */
    depth: number;
}

/**
 * Собирает дерево из плоского списка.
 *
 * <p>Бэк отдаёт подразделения списком (их десятки, форма дерева — презентация), поэтому сборка
 * живёт здесь. Чистая функция: ни запросов, ни состояния.</p>
 *
 * <p>Подразделение, чей родитель не найден в списке, показывается как корневое — потерять запись
 * из-за неполных данных хуже, чем показать её не на своём месте. Обход защищён от колец
 * (их не должно быть, но зацикливаться на битых данных интерфейс не обязан).</p>
 */
export function buildOrgUnitTree(units: OrgUnitDto[]): OrgUnitNode[] {
    const childrenOf = new Map<number | null, OrgUnitDto[]>();
    const knownIds = new Set(units.map((u) => u.id));

    for (const unit of units) {
        const parentKey =
            unit.parentId != null && knownIds.has(unit.parentId) ? unit.parentId : null;
        const siblings = childrenOf.get(parentKey) ?? [];
        siblings.push(unit);
        childrenOf.set(parentKey, siblings);
    }

    const visited = new Set<number>();
    const attach = (unit: OrgUnitDto, depth: number): OrgUnitNode => {
        visited.add(unit.id);
        const children = (childrenOf.get(unit.id) ?? [])
            .filter((child) => !visited.has(child.id))
            .map((child) => attach(child, depth + 1));
        return { ...unit, children, depth };
    };

    return (childrenOf.get(null) ?? []).map((root) => attach(root, 0));
}

/**
 * Разворачивает дерево обратно в плоский список в порядке обхода — с проставленной глубиной.
 * Нужен там, где дерево рисовать нечем: выпадающий выбор родителя или подразделения.
 */
export function flattenOrgUnitTree(nodes: OrgUnitNode[]): OrgUnitNode[] {
    return nodes.flatMap((node) => [node, ...flattenOrgUnitTree(node.children)]);
}

/**
 * Загрузка оргструктуры: плоский список, готовое дерево и перезагрузка.
 *
 * <p>Один источник на все места, где нужны подразделения (раздел «Оргструктура», выбор в формах
 * преподавателя и группы) — по образцу {@code usePeriod}, чтобы каждый новый потребитель не
 * заводил свою загрузку и свою сборку дерева.</p>
 */
export function useOrgUnits() {
    const [units, setUnits] = useState<OrgUnitDto[]>([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState<string | null>(null);

    const reload = useCallback(async () => {
        setLoading(true);
        setError(null);
        try {
            setUnits(await ResourceService.getOrgUnits());
        } catch (err) {
            console.error('Ошибка загрузки подразделений:', err);
            setError('Не удалось загрузить оргструктуру.');
        } finally {
            setLoading(false);
        }
    }, []);

    useEffect(() => {
        void reload();
    }, [reload]);

    const tree = useMemo(() => buildOrgUnitTree(units), [units]);
    const flat = useMemo(() => flattenOrgUnitTree(tree), [tree]);

    return { units, tree, flat, loading, error, reload };
}
