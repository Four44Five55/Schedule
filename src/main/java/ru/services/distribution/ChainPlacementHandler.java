package ru.services.distribution;

import lombok.extern.slf4j.Slf4j;
import ru.entity.CellForLesson;
import ru.entity.Lesson;
import ru.services.SlotChainService;
import ru.services.solver.PlacementOption;
import ru.services.solver.ScheduleWorkspace;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Обрабатывает размещение цепочек занятий.
 * Вынесены методы из DistributionDiscipline:
 * - getChainForLesson()
 * - tryPlaceChainInDay()
 */
@Slf4j
public class ChainPlacementHandler {
    private final SlotChainService slotChainService;
    private final DistributionContext context;
    private final ScheduleWorkspace workspace;

    public ChainPlacementHandler(SlotChainService slotChainService, DistributionContext context) {
        this.slotChainService = slotChainService;
        this.context = context;
        this.workspace = context.getWorkspace();
    }

    /**
     * Получает цепочку занятий для указанного занятия.
     */
    public List<Lesson> getChainForLesson(Lesson startLesson, List<Lesson> allSortedLessons) {
        List<Integer> chainSlotIds = slotChainService.getFullChain(startLesson.getCurriculumSlot().getId());

        if (chainSlotIds.size() <= 1) {
            return Collections.singletonList(startLesson);
        }

        Integer streamId = startLesson.getStudyStream().getId();

        List<Lesson> chain = allSortedLessons.stream()
                .filter(l -> chainSlotIds.contains(l.getCurriculumSlot().getId())
                        && l.getStudyStream().getId().equals(streamId))
                .sorted(Comparator.comparingInt(l -> l.getCurriculumSlot().getPosition()))
                .collect(Collectors.toList());

        if (!chain.isEmpty() && !chain.get(0).equals(startLesson)) {
            return Collections.singletonList(startLesson);
        }

        return chain;
    }

    /**
     * Пытается разместить цепочку занятий в указанный день.
     */
    public boolean tryPlaceChainInDay(List<Lesson> chain, List<CellForLesson> dayCells, boolean dayHasNoLecturesYet) {
        if (chain.isEmpty()) return true;
        if (dayCells.isEmpty()) return false;

        dayCells.sort(Comparator.comparing(CellForLesson::getTimeSlotPair));

        int chainSize = chain.size();

        for (int i = 0; i <= dayCells.size() - chainSize; i++) {
            List<PlacementOption> transaction = new ArrayList<>();
            boolean fit = true;

            for (int j = 0; j < chainSize; j++) {
                Lesson lesson = chain.get(j);
                CellForLesson cell = dayCells.get(i + j);

                // 1. Проверка непрерывности слотов
                if (j > 0) {
                    CellForLesson prevCell = dayCells.get(i + j - 1);
                    if (cell.getTimeSlotPair().ordinal() != prevCell.getTimeSlotPair().ordinal() + 1) {
                        fit = false;
                        break;
                    }
                }

                // 2. Правило 4-й пары
                if (cell.getTimeSlotPair() == ru.enums.TimeSlotPair.FOURTH) {
                    fit = false;
                    break;
                }

                // 3. Правило "Практики без лекций - не на 1-ю пару"
                boolean isLecture = lesson.getKindOfStudy() == ru.enums.KindOfStudy.LECTURE;
                if (!isLecture && dayHasNoLecturesYet && cell.getTimeSlotPair() == ru.enums.TimeSlotPair.FIRST) {
                    log.info("DEBUG: Правило 'не на 1-ю пару' СРАБОТАЛО: dayHasNoLecturesYet={}, lesson={}",
                            dayHasNoLecturesYet, lesson.getKindOfStudy());
                    fit = false;
                    break;
                }

                // 4. Проверка доступности (Workspace)
                PlacementOption option = workspace.findPlacementOption(lesson, cell);
                if (!option.isPossible()) {
                    fit = false;
                    break;
                }

                transaction.add(option);
            }

            // Если всё подошло
            if (fit) {
                // Коммитим транзакцию (размещаем реально)
                for (int j = 0; j < chainSize; j++) {
                    PlacementOption op = transaction.get(j);
                    workspace.executePlacement(op);
                    context.addDistributedLesson(op.lessonToPlace());
                }
                return true;
            }
        }

        return false;
    }
}
