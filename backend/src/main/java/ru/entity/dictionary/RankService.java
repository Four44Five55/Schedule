package ru.entity.dictionary;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/**
 * Род службы к специальному званию: «юстиции», «внутренней службы».
 *
 * <p>Отдельная ось, а не строки «полковник юстиции» в справочнике званий: иначе добавление новой
 * службы обязывало бы завести её ко всем нужным званиям вручную, а забытое звание выглядело бы
 * как «нет такого». Используется как исключение — у большинства преподавателей поле пустое.</p>
 */
@Entity
@Table(name = "rank_service")
public class RankService extends AbstractEducatorDictionary {
}
