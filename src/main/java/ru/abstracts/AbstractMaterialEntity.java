package ru.abstracts;

import ru.inter.IMaterialEntity;

public class AbstractMaterialEntity implements IMaterialEntity {
    protected int id;
    protected String name;

    public AbstractMaterialEntity() {
    }

    public AbstractMaterialEntity(int id, String name) {
        this.id = id;
        this.name = name;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }


}
