package ru.entity.logicSchema;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Менеджер для управления жесткими связями между учебными занятиями.
 * <p>
 * Обеспечивает создание и поддержание связей типа "занятие A должно идти
 * непосредственно перед занятием B в один день без других занятий между ними".
 * Связи сохраняются в базе данных через ассоциацию с {@link SlotChain} и могут быть
 * восстановлены после перезапуска приложения.
 * </p>
 *
 * <p><b>Пример использования:</b>
 * <pre>{@code
 * ChainManager manager = disciplineCurriculum.getChainManager();
 * manager.chain(slotA, slotB);
 * if (manager.hasNext(slotA)) {
 *     CurriculumSlot next = manager.getNext(slotA);
 * }
 * }</pre>
 * </p>
 *
 * @see SlotChain
 * @see DisciplineCurriculum
 */
@Entity
@Table(name = "slot_chain")
public class ChainManager {

    /**
     * Уникальный идентификатор менеджера связей
     */
    @Getter
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Учебный план, к которому относятся связи
     */
    @Getter
    @Setter
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "curriculum_id", nullable = false)
    private DisciplineCurriculum curriculum;

    /**
     * Коллекция связей между занятиями. Каждая связь представлена отдельной сущностью {@link SlotChain}
     */
    @OneToMany(mappedBy = "chainManager", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<SlotChain> slotChains = new HashSet<>();

    /**
     * Кеш связей "слот → следующий слот" (не сохраняется в БД)
     */
    @Transient
    private Map<Integer, Integer> nextSlotMap;

    /**
     * Кеш связей "слот → предыдущий слот" (не сохраняется в БД)
     */
    @Transient
    private Map<Integer, Integer> prevSlotMap;

    /**
     * Создает новый экземпляр ChainManager без инициализации связей.
     */
    public ChainManager() {
        this.nextSlotMap = new HashMap<>();
        this.prevSlotMap = new HashMap<>();
    }

    /**
     * Инициализирует кешированные карты связей на основе данных из БД.
     * Вызывается автоматически после загрузки или обновления сущности.
     */
    @PostLoad
    @PostPersist
    @PostUpdate
    private void initializeMaps() {
        this.nextSlotMap = slotChains.stream()
                .collect(Collectors.toMap(
                        sc -> sc.getSlotA().getId(),
                        sc -> sc.getSlotB().getId()
                ));

        this.prevSlotMap = slotChains.stream()
                .collect(Collectors.toMap(
                        sc -> sc.getSlotB().getId(),
                        sc -> sc.getSlotA().getId()
                ));
    }

    /**
     * Создает связь между двумя занятиями (A → B).
     *
     * @param slotA Первое занятие в связке (должно идти перед slotB)
     * @param slotB Второе занятие в связке (должно идти после slotA)
     * @throws IllegalArgumentException если попытка создать циклическую связь
     * @throws IllegalStateException если slotA или slotB уже имеют связи,
     *         несовместимые с создаваемой
     */
    public void chain(CurriculumSlot slotA, CurriculumSlot slotB) {
        if (slotA == null || slotB == null) {
            throw new IllegalArgumentException("Оба слота должны быть не null");
        }
        if (slotA.equals(slotB)) {
            throw new IllegalArgumentException("Нельзя связать слот с самим собой");
        }

        SlotChain chain = new SlotChain(this, slotA, slotB);
        slotChains.add(chain);
        updateMaps(slotA.getId(), slotB.getId());
    }

    /**
     * Удаляет связь между двумя занятиями.
     *
     * @param slotA Первое занятие в связке
     * @param slotB Второе занятие в связке
     * @return true если связь существовала и была удалена, false в противном случае
     */
    public boolean unchain(CurriculumSlot slotA, CurriculumSlot slotB) {
        boolean removed = slotChains.removeIf(sc ->
                sc.getSlotA().equals(slotA) && sc.getSlotB().equals(slotB));

        if (removed) {
            nextSlotMap.remove(slotA.getId());
            prevSlotMap.remove(slotB.getId());
        }
        return removed;
    }

    /**
     * Проверяет, имеет ли указанное занятие следующее занятие в цепочке.
     *
     * @param slot Занятие для проверки
     * @return true если есть следующее занятие, false в противном случае
     */
    public boolean hasNext(CurriculumSlot slot) {
        return nextSlotMap.containsKey(slot.getId());
    }

    /**
     * Проверяет, имеет ли указанное занятие предыдущее занятие в цепочке.
     *
     * @param slot Занятие для проверки
     * @return true если есть предыдущее занятие, false в противном случае
     */
    public boolean hasPrev(CurriculumSlot slot) {
        return prevSlotMap.containsKey(slot.getId());
    }

    /**
     * Возвращает следующее занятие в цепочке.
     *
     * @param slot Текущее занятие
     * @return Следующее занятие или null, если текущее занятие последнее в цепочке
     */
    public CurriculumSlot getNext(CurriculumSlot slot) {
        Integer nextId = nextSlotMap.get(slot.getId());
        return nextId != null ? findSlotById(nextId) : null;
    }

    /**
     * Возвращает предыдущее занятие в цепочке.
     *
     * @param slot Текущее занятие
     * @return Предыдущее занятие или null, если текущее занятие первое в цепочке
     */
    public CurriculumSlot getPrev(CurriculumSlot slot) {
        Integer prevId = prevSlotMap.get(slot.getId());
        return prevId != null ? findSlotById(prevId) : null;
    }

    /**
     * Проверяет, связаны ли два занятия напрямую.
     *
     * @param slotA Первое занятие
     * @param slotB Второе занятие
     * @return true если занятия связаны (A→B или B→A), false в противном случае
     */
    public boolean isChained(CurriculumSlot slotA, CurriculumSlot slotB) {
        return Objects.equals(nextSlotMap.get(slotA.getId()), slotB.getId())
                || Objects.equals(prevSlotMap.get(slotA.getId()), slotB.getId());
    }

    // ==================== Вспомогательные методы ====================

    private CurriculumSlot findSlotById(Integer id) {
        return curriculum.getCurriculumSlots().stream()
                .filter(s -> s.getId().equals(id))
                .findFirst()
                .orElse(null);
    }

    private void updateMaps(Integer slotAId, Integer slotBId) {
        nextSlotMap.put(slotAId, slotBId);
        prevSlotMap.put(slotBId, slotAId);
    }

    // ==================== Геттеры и сеттеры ====================

    public Set<SlotChain> getSlotChains() {
        return Collections.unmodifiableSet(slotChains);
    }
}