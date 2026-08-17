package ru.services.importing;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import ru.services.importing.EducatorNameDecoder.EducatorName;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Разбор подписи преподавателя. Все входные строки — <b>живые</b>: из подвала файла 911, из шапки
 * файла Ветрова и из имени файла выгрузки.
 *
 * <p>Список званий здесь тот же, что в справочнике (миграция 020) — функция берёт его входом, а не
 * знает сама.</p>
 */
class EducatorNameDecoderTest {

    private static final List<String> RANKS =
            List.of("мл л-т", "л-т", "ст л-т", "к-н", "м-р", "п/п-к", "п-к", "г-м", "г-л");

    private static EducatorName decode(String raw) {
        return EducatorNameDecoder.decode(raw, RANKS);
    }

    @Test
    @DisplayName("Звание-приставка снимается по справочнику")
    void stripsRank() {
        EducatorName name = decode("п/п-к Астахов С.В.");

        assertThat(name.recognized()).isTrue();
        assertThat(name.rank()).isEqualTo("п/п-к");
        assertThat(name.surname()).isEqualTo("Астахов");
        assertThat(name.initials()).isEqualTo("С.В.");
        assertThat(name.credentials()).isNull();
        assertThat(name.key()).isEqualTo("Астахов С.В.");
    }

    @Test
    @DisplayName("Регалии после инициалов уходят хвостом и в ключ не попадают")
    void keepsCredentialsOutOfKey() {
        EducatorName name = decode("Тополев В.Ф. двн проф");

        assertThat(name.rank()).isNull();
        assertThat(name.surname()).isEqualTo("Тополев");
        assertThat(name.credentials()).isEqualTo("двн проф");
        // Ключ один и тот же с регалиями и без: звание и степень меняются во времени.
        assertThat(name.key()).isEqualTo(decode("Тополев В.Ф.").key());
    }

    @Test
    @DisplayName("Звание и степень разом: «к-н Ветров Р.И. ктн»")
    void stripsRankAndCredentials() {
        EducatorName name = decode("к-н Ветров Р.И. ктн");

        assertThat(name.rank()).isEqualTo("к-н");
        assertThat(name.surname()).isEqualTo("Ветров");
        assertThat(name.initials()).isEqualTo("Р.И.");
        assertThat(name.credentials()).isEqualTo("ктн");
    }

    @Test
    @DisplayName("Имя файла без пробела — тот же разбор: якорь это инициалы")
    void readsFileNameForm() {
        EducatorName name = decode("ВетровР.И.");

        assertThat(name.surname()).isEqualTo("Ветров");
        assertThat(name.initials()).isEqualTo("Р.И.");
        assertThat(name.key()).isEqualTo("Ветров Р.И.");
    }

    @Test
    @DisplayName("Двойная фамилия не ломает разбор — эвристики «где кончается фамилия» здесь нет")
    void readsDoubleSurname() {
        assertThat(decode("Петров-Водкин А.А.").surname()).isEqualTo("Петров-Водкин");
        assertThat(decode("п-к Петров-Водкин А.А.").surname()).isEqualTo("Петров-Водкин");
    }

    @Test
    @DisplayName("Инициалы через пробел приводятся к «И.О.»")
    void normalizesSpacedInitials() {
        assertThat(decode("Зорина А. Ю. кфмн").initials()).isEqualTo("А.Ю.");
        assertThat(decode("Зорина А. Ю. кфмн").key()).isEqualTo(decode("Зорина А.Ю.").key());
    }

    @Test
    @DisplayName("Звание берётся самое длинное: «ст л-т» не читается как «л-т»")
    void prefersLongestRank() {
        assertThat(decode("ст л-т Иванов И.И.").rank()).isEqualTo("ст л-т");
        assertThat(decode("мл л-т Иванов И.И.").surname()).isEqualTo("Иванов");
    }

    @Test
    @DisplayName("Похожее на звание начало фамилии званием не считается")
    void doesNotEatSurnameStart() {
        // «п-к» отделяется пробелом, поэтому фамилия «П-ковский» цела.
        EducatorName name = decode("П-ковский И.И.");

        assertThat(name.rank()).isNull();
        assertThat(name.surname()).isEqualTo("П-ковский");
    }

    @Test
    @DisplayName("Без справочника званий разбор продолжается, звание просто не снимается")
    void worksWithoutRankDictionary() {
        EducatorName name = EducatorNameDecoder.decode("Северова Е.В.", List.of());

        assertThat(name.recognized()).isTrue();
        assertThat(name.surname()).isEqualTo("Северова");
    }

    @Test
    @DisplayName("Нет инициалов — отказ с причиной, а не догадка")
    void reportsMissingInitials() {
        EducatorName name = decode("Кафедра информатики");

        assertThat(name.recognized()).isFalse();
        assertThat(name.problem()).contains("инициалы");
        assertThat(name.key()).isNull();
    }

    @Test
    @DisplayName("Точка между фамилией и инициалами — разделитель, а не часть фамилии")
    void dotBeforeInitialsIsSeparator() {
        // Живой случай: «Иванов.И.И.» давал ключ «Иванов. И.И.» и молча не находился в базе.
        assertThat(decode("Иванов.И.И.").surname()).isEqualTo("Иванов");
        assertThat(decode("Иванов.И.И.").key()).isEqualTo(decode("Иванов И.И.").key());
        assertThat(decode("п-к Иванов.И.И.").rank()).isEqualTo("п-к");
    }

    @Test
    @DisplayName("Точки после отчества может не быть — человек тот же")
    void trailingDotIsOptional() {
        assertThat(decode("Иванов И.И").recognized()).isTrue();
        assertThat(decode("Иванов И.И").key()).isEqualTo(decode("Иванов И.И.").key());
        assertThat(decode("к-н Ветров Р.И ктн").credentials()).isEqualTo("ктн");
    }

    @Test
    @DisplayName("Первая точка обязательна: «Иванов ИИ» от обычного текста не отличить")
    void firstDotIsRequired() {
        assertThat(decode("Иванов ИИ").recognized()).isFalse();
    }

    @Test
    @DisplayName("Ключ нашего имени: обе формы записи дают одно и то же")
    void keyOfStoredNameNormalizesBothForms() {
        assertThat(EducatorNameDecoder.keyOfStoredName("Иванов И.И.")).isEqualTo("Иванов И.И.");
        assertThat(EducatorNameDecoder.keyOfStoredName("Иванов Иван Иванович")).isEqualTo("Иванов И.И.");
        // Ключ файла и ключ базы сходятся — ради этого обе стороны и приводятся к одной форме.
        assertThat(EducatorNameDecoder.keyOfStoredName("Ветров Роман Игоревич"))
                .isEqualTo(decode("к-н Ветров Р.И. ктн").key());
    }

    @Test
    @DisplayName("Звание, затесавшееся в наше имя, ключ не портит — если дать справочник")
    void keyOfStoredNameStripsRankFromOurData() {
        // По модели звание живёт отдельным полем, но данные заводились руками: «п-к Иванов И.И.»
        // в educator.name возможен, и без справочника такой человек не нашёлся бы никогда.
        assertThat(EducatorNameDecoder.keyOfStoredName("п-к Иванов И.И.", RANKS)).isEqualTo("Иванов И.И.");
        assertThat(EducatorNameDecoder.keyOfStoredName("п-к Иванов И.И.")).isEqualTo("п-к Иванов И.И.");
    }

    @Test
    @DisplayName("Ключ без отчества короче и с полной формой не совпадает — склеивать нельзя")
    void keyWithoutPatronymicDoesNotCollide() {
        assertThat(EducatorNameDecoder.keyOfStoredName("Иванов Иван")).isEqualTo("Иванов И.");
        assertThat(EducatorNameDecoder.keyOfStoredName("Иванов")).isNull();
    }

    @Test
    @DisplayName("Пустая подпись и мусор не бросают исключений")
    void isTotal() {
        assertThat(decode(null).recognized()).isFalse();
        assertThat(decode("   ").recognized()).isFalse();
        assertThat(decode("А.А.").problem()).contains("фамилии");
    }
}
