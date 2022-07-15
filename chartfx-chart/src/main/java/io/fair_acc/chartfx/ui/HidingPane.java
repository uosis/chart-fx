package io.fair_acc.chartfx.ui;

import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.beans.property.*;
import javafx.event.EventHandler;
import javafx.geometry.Side;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Region;
import javafx.scene.shape.Rectangle;
import javafx.util.Duration;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * A small node wrapper which makes the node only appear in cloe proximity to the cursor. Aims to locally replace hidden side pane.
 */
public class HidingPane extends Region {
    public final ObjectProperty<Region> node = new SimpleObjectProperty<>(this, "node", null);
    public final DoubleProperty triggerDistance = new SimpleDoubleProperty(this, "triggerDistance", 50.0);
    public final ObjectProperty<Side> side = new SimpleObjectProperty<>(this, "side", Side.TOP);
    public final ObjectProperty<Duration> duration = new SimpleObjectProperty<>(this, "duration", Duration.millis(200));
    public final ObjectProperty<Duration> delay = new SimpleObjectProperty<>(this, "delay", Duration.millis(50));
    public final BooleanProperty sticky = new SimpleBooleanProperty(this, "sticky", false);
    private final DoubleProperty visibility = new SimpleDoubleProperty(this, "visibility", 0);
    private Timeline showTimeline;
    private Timeline hideTimeline;
    private Node lastHideBlockingNode = null;
    private long blockedSince = 0;

    public HidingPane() {
        node.addListener((bean, oldVal, newVal) -> getChildren().setAll(newVal));
        parentProperty().addListener((bean, oldVal, newVal) -> {
            if (oldVal != null) {
                oldVal.removeEventHandler(MouseEvent.MOUSE_MOVED, this::mouseHandler);
            }
            if (newVal != null && !sticky.get()) {
                newVal.addEventHandler(MouseEvent.MOUSE_MOVED, this::mouseHandler);
            }
        });
        sticky.addListener((b, o, n) -> {
            if (n) {
                getParent().addEventHandler(MouseEvent.MOUSE_MOVED, this::mouseHandler);
            } else {
                getParent().removeEventHandler(MouseEvent.MOUSE_MOVED, this::mouseHandler);
                show();
            }
        });
        EventHandler<MouseEvent> handler = (MouseEvent ev) -> {
            sticky.set(!sticky.get());
            ev.consume();
        };
        node.addListener((b, o, n) -> {
            if (o != null) {
                o.removeEventHandler(MouseEvent.MOUSE_CLICKED, handler);
            }
            if (n != null && !sticky.get()) {
                n.addEventHandler(MouseEvent.MOUSE_CLICKED, handler);
            }
        });
        visibility.addListener((b, o, n) -> {
            requestLayout();
        });
    }

    public HidingPane(final Region node) {
        this();
        this.node.set(node);
    }

    private void mouseHandler(final MouseEvent t) {
        if (Math.abs(t.getY() - this.getBoundsInParent().getMinY()) > triggerDistance.get()) {
            hide();
        } else {
            show();
        }
    }

    private void hide() {
        if (showTimeline != null) {
            showTimeline.stop();
        }

        if (hideTimeline != null && hideTimeline.getStatus() == Animation.Status.RUNNING) {
            return;
        }

        // check for children having focus (eg Combo boxes/menus)
        if (hasShowingChild(lastHideBlockingNode) || hasShowingChild(node.get())) {
            final long now = System.currentTimeMillis();
            if (blockedSince == 0) {
                blockedSince = now;
            }
            if ((now - blockedSince) < duration.get().toMillis()) {
                return;
            }
        }
        blockedSince = 0;

        // collapse open menus/comboboxes before hiding side pane
        if (hasShowingChild(lastHideBlockingNode)) {
            Method closeMethod = getMethod(lastHideBlockingNode.getClass(), "hide");
            if (closeMethod != null) {
                try {
                    closeMethod.invoke(lastHideBlockingNode);
                } catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
                    // do nothing
                }
            }
        }

        final KeyValue keyValue = new KeyValue(visibility, 0);

        final KeyFrame keyFrame = new KeyFrame(duration.get(), keyValue);
        hideTimeline = new Timeline(keyFrame);
        hideTimeline.setDelay(delay.get());
        hideTimeline.play();
    }

    private void show() {
        if (hideTimeline != null) {
            hideTimeline.stop();
        }

        if (showTimeline != null && showTimeline.getStatus() == Animation.Status.RUNNING) {
            return;
        }

        final KeyValue keyValue = new KeyValue(visibility, 1);

        final KeyFrame keyFrame = new KeyFrame(duration.get(), keyValue);
        showTimeline = new Timeline(keyFrame);
        showTimeline.setDelay(delay.get());
        showTimeline.play();
    }

    private boolean hasShowingChild(Node n) {
        if (n == null) {
            return false;
        }
        if (n.isHover()) {
            lastHideBlockingNode = n;
            return true;
        }
        try {
            Method showingMethod = getMethod(n.getClass(), "isShowing");
            if (showingMethod != null && (Boolean) showingMethod.invoke(n)) {
                lastHideBlockingNode = n;
                return true;
            }
        } catch (SecurityException | IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
            // do nothing
        }
        if (n instanceof Parent) {
            return ((Parent) n).getChildrenUnmodifiable().stream().anyMatch(this::hasShowingChild);
        }
        return false;
    }
    public static Method getMethod(final Class<?> clazz, final String methodName) {
        try {
            return clazz.getMethod(methodName);
        } catch (NoSuchMethodException | SecurityException e) {
            return null;
        }
    }

    @Override
    protected void layoutChildren() {
        var n = node.get();
        var v = sticky.get() ? 1.0 : visibility.get();
        if (v > 0) {
            switch (side.get()){
                case TOP:
                    final var w = getWidth();
                    final var h = n.prefHeight(w) * v;
                    n.resizeRelocate(0,0 , w, h);
                    setClip(new Rectangle(0, 0, w, h));
                    break;
                case BOTTOM:
                    final var w2 = getWidth();
                    final var h2 = n.prefHeight(w2) * v;
                    n.resizeRelocate(h2 - n.prefHeight(w2),0 , w2, h2);
                    setClip(new Rectangle(0, 0, w2, h2));
                    break;
                case LEFT:
                    final var h3 = getHeight();
                    final var w3 = n.prefWidth(h3) * v;
                    n.resizeRelocate(0,0 , w3, h3);
                    setClip(new Rectangle(0, 0, w3, h3));
                    break;
                case RIGHT:
                    final var h4 = getHeight();
                    final var w4 = n.prefWidth(h4) * v;
                    n.resizeRelocate(0,w4 - n.prefWidth(h4) , w4, h4);
                    setClip(new Rectangle(0, 0, w4, h4));
                    break;
            }
            n.setVisible(true);
            n.setManaged(true);
        } else {
            n.setVisible(false);
            n.setManaged(false);
        }
    }
}
