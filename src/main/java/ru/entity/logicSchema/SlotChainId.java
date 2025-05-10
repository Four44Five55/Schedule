package ru.entity.logicSchema;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;

@Getter
@Setter
@Embeddable
@NoArgsConstructor
@AllArgsConstructor
public class SlotChainId implements Serializable {

    @Column(name = "curriculum_id")
    private Long curriculumId;

    @Column(name = "slot_a_id")
    private Integer slotAId;

    @Column(name = "slot_b_id")
    private Integer slotBId;
}
