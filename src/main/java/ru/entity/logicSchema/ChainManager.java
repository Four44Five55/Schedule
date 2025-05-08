package ru.entity.logicSchema;

import java.util.*;

/**
 * Менеджер для хранения жёстких связей между занятиями.
 * Занятие A должно идти сразу перед занятием B без перерывов в один день, не имея между собой иных занятий.
 */
public final class ChainManager {
    // key: ID слота, value: ID следующего неразрывно связанного слота
    private final Map<Integer, Integer> nextSlotMap = new HashMap<>();
    // Обратная связь для быстрой проверки "что перед B?"
    private final Map<Integer, Integer> prevSlotMap = new HashMap<>();

    /**
     * Связывает два занятия (A → B).
     *
     * @param slotAId ID первого занятия (A)
     * @param slotBId ID второго занятия (B)
     */
    public void chain(int slotAId, int slotBId) {
        nextSlotMap.put(slotAId, slotBId);
        prevSlotMap.put(slotBId, slotAId);
    }

    /**
     * Проверяет, есть ли у занятия следующее в цепочке.
     */
    public boolean hasNext(int slotId) {
        return nextSlotMap.containsKey(slotId);
    }

    /**
     * Проверяет, есть ли у занятия предыдущее в цепочке.
     */
    public boolean hasPrev(int slotId) {
        return prevSlotMap.containsKey(slotId);
    }

    /**
     * Возвращает ID следующего занятия в цепочке (или null).
     */
    public Integer getNext(int slotId) {
        return nextSlotMap.get(slotId);
    }

    /**
     * Возвращает ID предыдущего занятия в цепочке (или null).
     */
    public Integer getPrev(int slotId) {
        return prevSlotMap.get(slotId);
    }

    /**
     * Возвращает все цепочки связей между слотами в виде Map.
     * Ключ - ID исходного слота (A), значение - ID связанного слота (B) (A → B).
     *
     * @return Неизменяемая карта всех связей
     */
    public Map<Integer, Integer> getAllChains() {
        return Collections.unmodifiableMap(new HashMap<>(nextSlotMap));
    }

    /**
     * Разрывает связь между занятиями.
     */
    public void unchain(int slotAId, int slotBId) {
        nextSlotMap.remove(slotAId);
        prevSlotMap.remove(slotBId);
    }

    /**
     * Проверяет, связаны ли два занятия.
     */
    public boolean isChained(int slotAId, int slotBId) {
        return Objects.equals(nextSlotMap.get(slotAId), slotBId)
                || Objects.equals(prevSlotMap.get(slotAId), slotBId);
    }

}
