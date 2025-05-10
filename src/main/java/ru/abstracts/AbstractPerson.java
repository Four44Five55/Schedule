package ru.abstracts;

import lombok.Getter;
import lombok.NoArgsConstructor;
import ru.inter.Person;

import java.util.Objects;
@Getter
@NoArgsConstructor
abstract public class AbstractPerson extends AbstractMaterialEntity  implements Person {

    public AbstractPerson(String name) {
        super(name);
    }

    @Override
    public String toString() {
        return
                "id: " + id + " " + name + '\'';
    }

}

