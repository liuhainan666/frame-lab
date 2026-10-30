package com.framelab.task.render;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;

class LayoutPlannerTest {

    private final LayoutPlanner planner = new LayoutPlanner();

    @Test
    void plansSingleImageAcrossWholeCanvas() {
        List<LayoutSlot> slots = planner.plan(1, 1600, 1200);

        assertThat(slots).containsExactly(new LayoutSlot(0, 0, 1600, 1200));
    }

    @Test
    void plansTwoColumnsWithGapAndNoOverlap() {
        List<LayoutSlot> slots = planner.plan(2, 1600, 1200);

        assertThat(slots).hasSize(2);
        LayoutSlot left = slots.get(0);
        LayoutSlot right = slots.get(1);
        assertThat(left.y()).isZero();
        assertThat(right.y()).isZero();
        assertThat(left.height()).isEqualTo(1200);
        assertThat(right.height()).isEqualTo(1200);
        assertThat(left.x() + left.width()).isLessThan(right.x());
        assertThat(right.x() - (left.x() + left.width())).isEqualTo(12);
        assertThat(right.x() + right.width()).isEqualTo(1600);
        assertThat(left.width()).isEqualTo(right.width());
    }

    @Test
    void plansThreeSlotsWithTopFeatureAndBottomPair() {
        List<LayoutSlot> slots = planner.plan(3, 1600, 1200);

        assertThat(slots).hasSize(3);
        LayoutSlot top = slots.get(0);
        LayoutSlot bottomLeft = slots.get(1);
        LayoutSlot bottomRight = slots.get(2);
        assertThat(top).isEqualTo(new LayoutSlot(0, 0, 1600, Math.round(1200 * 0.62f)));
        assertThat(bottomLeft.y()).isEqualTo(bottomRight.y());
        assertThat(bottomLeft.y() - (top.y() + top.height())).isEqualTo(12);
        assertThat(bottomLeft.x() + bottomLeft.width()).isLessThan(bottomRight.x());
        assertThat(bottomRight.x() + bottomRight.width()).isEqualTo(1600);
        assertThat(bottomLeft.y() + bottomLeft.height()).isEqualTo(1200);
        assertThat(bottomLeft.width()).isEqualTo(bottomRight.width());
    }

    @Test
    void rejectsUnsupportedCountsAndTinyCanvas() {
        assertThatThrownBy(() -> planner.plan(0, 1600, 1200))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> planner.plan(4, 1600, 1200))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> planner.plan(3, 40, 40))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
