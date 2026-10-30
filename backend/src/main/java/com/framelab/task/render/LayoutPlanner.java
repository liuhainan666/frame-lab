package com.framelab.task.render;

import java.util.List;

import org.springframework.stereotype.Component;

@Component
public class LayoutPlanner {

    private static final int GAP = 12;

    public List<LayoutSlot> plan(int imageCount, int canvasWidth, int canvasHeight) {
        if (imageCount < 1 || imageCount > 3) {
            throw new IllegalArgumentException("Unsupported material count: " + imageCount);
        }
        if (canvasWidth < 64 || canvasHeight < 64) {
            throw new IllegalArgumentException("Canvas is too small");
        }
        return switch (imageCount) {
            case 1 -> List.of(new LayoutSlot(0, 0, canvasWidth, canvasHeight));
            case 2 -> twoColumns(canvasWidth, canvasHeight);
            default -> threeSlots(canvasWidth, canvasHeight);
        };
    }

    private List<LayoutSlot> twoColumns(int width, int height) {
        int leftWidth = (width - GAP) / 2;
        int rightX = leftWidth + GAP;
        int rightWidth = width - rightX;
        requirePositive(leftWidth, rightWidth, height);
        return List.of(
                new LayoutSlot(0, 0, leftWidth, height),
                new LayoutSlot(rightX, 0, rightWidth, height));
    }

    private List<LayoutSlot> threeSlots(int width, int height) {
        int topHeight = Math.round(height * 0.62f);
        int bottomY = topHeight + GAP;
        int bottomHeight = height - bottomY;
        int bottomLeftWidth = (width - GAP) / 2;
        int bottomRightX = bottomLeftWidth + GAP;
        int bottomRightWidth = width - bottomRightX;
        requirePositive(topHeight, bottomHeight, bottomLeftWidth, bottomRightWidth);
        return List.of(
                new LayoutSlot(0, 0, width, topHeight),
                new LayoutSlot(0, bottomY, bottomLeftWidth, bottomHeight),
                new LayoutSlot(bottomRightX, bottomY, bottomRightWidth, bottomHeight));
    }

    private void requirePositive(int... values) {
        for (int value : values) {
            if (value <= 0) {
                throw new IllegalArgumentException("Canvas is too small for the layout");
            }
        }
    }
}
