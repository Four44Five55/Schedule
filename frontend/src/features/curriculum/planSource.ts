import { CurriculumSlotDto, KindOfStudy, SlotChainDto } from '../../types/api';
import { CurriculumService } from '../../services/apiServices';

/**
 * Значения формы одного слота учебного плана — общий «поднабор» полей, не зависящий
 * от того, куда слот сохраняется (курс сейчас, шаблон в будущем).
 */
export interface SlotFormValues {
  position: number;
  kindOfStudy: KindOfStudy;
  themeLessonId?: number;
  requiredAuditoriumId?: number;
  priorityAuditoriumId?: number;
  allowedAuditoriumPoolId?: number;
}

/**
 * Strategy/Repository: откуда редактор плана берёт слоты и куда их пишет. Редактор
 * зависит от этого интерфейса, а не от конкретного курса (DIP) — поэтому тот же
 * редактор позже переиспользуется под шаблон плана (`templateSlotSource`) без правок.
 *
 * <p>Сознательно НЕ содержит вычисление следующей позиции: это чистая функция от уже
 * загруженного списка (забота редактора), а не операция доступа к данным.</p>
 */
export interface CurriculumPlanSource {
  list(): Promise<CurriculumSlotDto[]>;
  create(values: SlotFormValues): Promise<CurriculumSlotDto>;
  update(id: number, values: SlotFormValues): Promise<CurriculumSlotDto>;
  remove(id: number): Promise<void>;

  // Сцепки (неразрывность соседних занятий). Редактор фильтрует их по своим слотам.
  listChains(): Promise<SlotChainDto[]>;
  link(slotAId: number, slotBId: number): Promise<void>;
  unlink(chainId: number): Promise<void>;
}

/**
 * Источник «слоты курса» — текущая реализация поверх curriculum-slots API.
 * Подставляет `disciplineCourseId` в payload создания; обновление позицию не трогает
 * (порядок слотов авторитетно ведёт бэкенд при вставке/удалении).
 */
export const courseSlotSource = (courseId: number): CurriculumPlanSource => ({
  list: () => CurriculumService.getSlotsByCourse(courseId),
  create: (v) => CurriculumService.createSlot({ disciplineCourseId: courseId, ...v }),
  update: (id, v) => CurriculumService.updateSlot(id, {
    kindOfStudy: v.kindOfStudy,
    themeLessonId: v.themeLessonId,
    requiredAuditoriumId: v.requiredAuditoriumId,
    priorityAuditoriumId: v.priorityAuditoriumId,
    allowedAuditoriumPoolId: v.allowedAuditoriumPoolId,
  }),
  remove: (id) => CurriculumService.deleteSlot(id),

  // /slot-chains отдаёт все сцепки; редактор отфильтрует по слотам курса.
  listChains: () => CurriculumService.getSlotChains(),
  link: (a, b) => CurriculumService.createChain({ slotAId: a, slotBId: b }).then(() => {}),
  unlink: (id) => CurriculumService.deleteChain(id),
});
