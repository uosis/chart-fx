package io.fair_acc.chartfx.ui.layout;

import io.fair_acc.chartfx.ui.geometry.Corner;
import io.fair_acc.chartfx.ui.geometry.Side;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.scene.Node;
import javafx.scene.layout.Region;

import java.util.EnumMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * A 3x3 grid layout consisting of up to 4 sides, 4 corners, and a content area
 * The sides and corners are sized automatically, and the content uses whatever
 * space is left in the center.
 * <p>
 * TL    -   TOP      -   TR
 * LEFT  -   CONTENT  -   RIGHT
 * BL    -   BOTTOM   -   BR
 * <p>
 * TODO: disallow computePrefWidth and computePrefHeight?
 * TODO: remove nodes if the area is too small?
 *
 * @author Florian Enner
 * @since 22 Jun 2023
 */
public class GridLayout extends Region {

    public static GridLayout create() {
        return new GridLayout();
    }

    public static GridLayout create(Consumer<GridLayout> init) {
        var region = new GridLayout();
        init.accept(region);
        return region;
    }

    public Node getSide(Side side) {
        return sides.get(side);
    }

    public Node getCorner(Corner side) {
        return corners.get(side);
    }

    public void setSide(Side side, Node node) {
        Node previous = sides.put(side, node);
        replace(previous, node);
    }

    public void setCorner(Corner corner, Node node) {
        Node previous = corners.put(corner, node);
        replace(previous, node);
    }

    public ObservableList<Node> getContentNodes() {
        return content;
    }

    private void replace(Node previous, Node node) {
        if (previous != null) {
            getChildren().remove(previous);
        }
        if (getChildren().contains(node)) {
            throw new IllegalArgumentException("The node is already a child");
        }
        getChildren().add(node);
    }

    private final Map<Side, Node> sides = new EnumMap<>(Side.class);
    private final Map<Corner, Node> corners = new EnumMap<>(Corner.class);
    private final ObservableList<Node> content = FXCollections.observableArrayList();

    {
        content.addListener((ListChangeListener<Node>) c -> {
            while (c.next()) {
                if (c.getRemovedSize() > 0) {
                    getChildren().removeAll(c.getRemoved());
                }
                if (c.getAddedSize() > 0) {
                    getChildren().addAll(c.getAddedSubList());
                }
            }
        });
    }

    @Override
    protected void layoutChildren() {
        // Account for margin and border insets
        final double xLeft = snappedLeftInset();
        final double yTop = snappedTopInset();
        final double width = snapSizeX(getWidth()) - xLeft - snappedRightInset();
        final double height = snapSizeY(getHeight()) - yTop - snappedBottomInset();

        var bottom = getSide(Side.BOTTOM);
        var top = getSide(Side.TOP);
        var right = getSide(Side.RIGHT);
        var left = getSide(Side.LEFT);
        var centerVer = getSide(Side.CENTER_VER);
        var centerHor = getSide(Side.CENTER_HOR);

        // Determine sizes
        var topHeight = getPrefHeight(top, width);
        var bottomHeight = getPrefHeight(bottom, width);
        var contentHeight = height - topHeight - bottomHeight;

        var leftWidth = getPrefWidth(left, contentHeight);
        var rightWidth = getPrefWidth(right, contentHeight);
        var contentWidth = width - leftWidth - rightWidth;

        var xContent = xLeft + leftWidth;
        var yContent = yTop + topHeight;

        var xRight = xContent + contentWidth;
        var yBottom = yContent + contentHeight;

        var horizontalHeight = getPrefHeight(centerVer, contentWidth);
        var verticalWidth = getPrefWidth(centerHor, contentHeight);

        // Outside
        resizeRelocate(top, xContent, yTop, contentWidth, topHeight);
        resizeRelocate(bottom, xContent, yBottom, contentWidth, bottomHeight);
        resizeRelocate(left, xLeft, yContent, leftWidth, contentHeight);
        resizeRelocate(right, xRight, yContent, rightWidth, contentHeight);

        // Center
        resizeRelocate(centerHor, xContent, yContent + contentHeight / 2 - horizontalHeight / 2, contentWidth, horizontalHeight);
        resizeRelocate(centerVer, xContent + contentWidth / 2 - verticalWidth / 2, yContent, verticalWidth, contentHeight);

        // Corners
        resizeRelocate(getCorner(Corner.TOP_LEFT), xLeft, yTop, leftWidth, topHeight);
        resizeRelocate(getCorner(Corner.TOP_RIGHT), xRight, yTop, rightWidth, topHeight);
        resizeRelocate(getCorner(Corner.BOTTOM_LEFT), xLeft, yBottom, leftWidth, bottomHeight);
        resizeRelocate(getCorner(Corner.BOTTOM_RIGHT), xRight, yBottom, rightWidth, bottomHeight);

        // Content
        for (Node node : content) {
            resizeRelocate(node, xContent, yContent, contentWidth, contentHeight);
        }

    }

    static void resizeRelocate(Node node, double x, double y, double width, double height) {
        if (node != null) {
            node.resizeRelocate(x, y, width, height);
        }
    }

    static double getPrefHeight(Node node, double width) {
        if (node == null || !node.isVisible()) {
            return 0;
        }
        return node.prefHeight(width);
    }

    static double getPrefWidth(Node node, double height) {
        if (node == null || !node.isVisible()) {
            return 0;
        }
        return node.prefWidth(height);
    }

}
