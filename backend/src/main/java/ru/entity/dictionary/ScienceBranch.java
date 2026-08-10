package ru.entity.dictionary;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/**
 * Отрасль науки учёной степени: «технические», «физико-математические».
 *
 * <p>Вторая половина степени: уровень (кандидат/доктор) — {@link ru.enums.AcademicDegree},
 * отрасль — здесь. Готовое сокращение «к.т.н.» не хранится нигде: оно собирается из уровня и
 * отрасли, иначе появилось бы третье представление одного факта.</p>
 *
 * <p>{@code shortName} — буквенная часть без точек: «т» даёт «к.т.н.», «ф-м» даёт «к.ф-м.н.».</p>
 */
@Entity
@Table(name = "science_branch")
public class ScienceBranch extends AbstractEducatorDictionary {
}
