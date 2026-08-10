package ru.entity.dictionary;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/**
 * Специальное (воинское) звание преподавателя: «полковник», «майор».
 *
 * <p>Справочник, а не enum: набор званий зависит от вуза, и пользователь ведёт его сам —
 * добавление значения не должно требовать релиза. Род службы («юстиции») сюда <b>не</b>
 * склеивается: это независимая ось, у неё свой справочник {@link RankService}.</p>
 */
@Entity
@Table(name = "special_rank")
public class SpecialRank extends AbstractEducatorDictionary {
}
