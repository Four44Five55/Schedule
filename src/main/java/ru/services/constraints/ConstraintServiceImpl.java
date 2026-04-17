package ru.services.constraints;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.entity.CellForLesson; // импортируем
import ru.entity.constraints.AuditoriumConstraint;
import ru.entity.constraints.ConstraintData;
import ru.entity.constraints.EducatorConstraint;
import ru.entity.constraints.GroupConstraint;
import ru.enums.TimeSlotPair; // импортируем
import ru.repository.constraints.AuditoriumConstraintRepository;
import ru.repository.constraints.EducatorConstraintRepository;
import ru.repository.constraints.GroupConstraintRepository;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ConstraintServiceImpl implements ConstraintService {

    private final EducatorConstraintRepository educatorConstraintRepository;
    private final GroupConstraintRepository groupConstraintRepository;
    private final AuditoriumConstraintRepository auditoriumConstraintRepository;

    @Override
    @Transactional(readOnly = true)
    public AllConstraints loadAllConstraints() {

        Map<Integer, List<ConstraintData>> educatorConstraintsMap = new HashMap<>();
        Map<Integer, List<ConstraintData>> groupConstraintsMap = new HashMap<>();
        Map<Integer, List<ConstraintData>> auditoriumConstraintsMap = new HashMap<>();

        // === Обработка ограничений для ПРЕПОДАВАТЕЛЕЙ ===
        List<EducatorConstraint> educatorConstraints = educatorConstraintRepository.findAll();
        for (EducatorConstraint constraint : educatorConstraints) {
            Integer educatorId = constraint.getEducator().getId();
            List<ConstraintData> dataList = educatorConstraintsMap.computeIfAbsent(educatorId, k -> new ArrayList<>());

            // "Разворачиваем" диапазон дат в конкретные ячейки CellForLesson
            for (LocalDate date = constraint.getStartDate(); !date.isAfter(constraint.getEndDate()); date = date.plusDays(1)) {
                // Если ограничение на весь день, добавляем все пары
                for (TimeSlotPair pair : TimeSlotPair.values()) {
                    dataList.add(new ConstraintData(new CellForLesson(date, pair), constraint.getKindOfConstraint()));
                }
            }
        }

        // === Обработка ограничений для ГРУПП ===
        List<GroupConstraint> groupConstraints = groupConstraintRepository.findAll();
        for (GroupConstraint constraint : groupConstraints) {
            Integer groupId = constraint.getGroup().getId();
            List<ConstraintData> dataList = groupConstraintsMap.computeIfAbsent(groupId, k -> new ArrayList<>());

            for (LocalDate date = constraint.getStartDate(); !date.isAfter(constraint.getEndDate()); date = date.plusDays(1)) {
                for (TimeSlotPair pair : TimeSlotPair.values()) {
                    dataList.add(new ConstraintData(new CellForLesson(date, pair), constraint.getKindOfConstraint()));
                }
            }
        }

        // === Обработка ограничений для АУДИТОРИЙ ===
        List<AuditoriumConstraint> auditoriumConstraints = auditoriumConstraintRepository.findAll();
        for (AuditoriumConstraint constraint : auditoriumConstraints) {
            Integer auditoriumId = constraint.getAuditorium().getId();
            List<ConstraintData> dataList = auditoriumConstraintsMap.computeIfAbsent(auditoriumId, k -> new ArrayList<>());

            for (LocalDate date = constraint.getStartDate(); !date.isAfter(constraint.getEndDate()); date = date.plusDays(1)) {
                for (TimeSlotPair pair : TimeSlotPair.values()) {
                    dataList.add(new ConstraintData(new CellForLesson(date, pair), constraint.getKindOfConstraint()));
                }
            }
        }

        return new AllConstraints(educatorConstraintsMap, groupConstraintsMap, auditoriumConstraintsMap);
    }
}
