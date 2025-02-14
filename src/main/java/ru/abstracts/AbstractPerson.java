package ru.abstracts;

import org.example.inter.Person;

abstract public class AbstractPerson implements Person {

    private int id;
    private String name;

    @Override
    public int getId() {
        return id;
    }

    @Override
    public String getName() {
        return name;
    }
}

