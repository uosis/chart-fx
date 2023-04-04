package io.fair_acc.chartfx.plugins;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.collections.ObservableList;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.ListCell;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.LinearGradient;
import javafx.scene.shape.Rectangle;

import io.fair_acc.chartfx.renderer.spi.utils.ColorGradient;

/**
 * Adds a Dropdown to the toolbar to select different Colormaps. The selected Colormap can be accessed 
 * from via colormapProperty() and bound to Renderers/Axes.
 *
 * @author Alexander Krimm
 */
public class ColormapSelector extends ChartPlugin {
    private final BooleanProperty showInToolbar = new SimpleBooleanProperty(this, "show in toolbar", true);
    private final ComboBox<ColorGradient> dropdown = new ColormapComboBox();

    public ColormapSelector() {
        super();
        chartProperty().addListener((change, o, n) -> {
            if (o != null) {
                o.getToolBar().getItems().remove(dropdown);
            }
            if (n != null && isShowInToolbar()) {
                n.getToolBar().getItems().add(dropdown);
            }
        });
        showInToolbar.addListener((prop, o, n) -> {
            if (Boolean.TRUE.equals(n)) {
                getChart().getToolBar().getItems().add(dropdown);
            } else {
                getChart().getToolBar().getItems().remove(dropdown);
            }
        });
    }

    public ObjectProperty<ColorGradient> colormapProperty() {
        return dropdown.valueProperty();
    }

    public ColorGradient getColormap() {
        return dropdown.getValue();
    }

    public ObservableList<ColorGradient> getGradientsList() {
        return dropdown.getItems();
    }

    public boolean isShowInToolbar() {
        return showInToolbar.get();
    }

    public void setColormap(final ColorGradient newGradient) {
        if (!getGradientsList().contains(newGradient)) {
            getGradientsList().add(newGradient);
        }
        dropdown.setValue(newGradient);
    }

    public void setShowInToolbar(final boolean show) {
        showInToolbar.set(show);
    }

    public BooleanProperty showInToolbarProperty() {
        return showInToolbar;
    }

    public static class ColormapComboBox extends ComboBox<ColorGradient> {
        public ColormapComboBox() {
            super();
            setCellFactory(listView -> new ColormapListCell());
            setButtonCell(new ColormapListCell());
            getItems().addAll(ColorGradient.colorGradients());
            setValue(ColorGradient.DEFAULT);
        }
    }

    public static class ColormapListCell extends ListCell<ColorGradient> {
        private static final double COLORMAP_WIDTH = 30;
        private static final double COLORMAP_HEIGHT = 10;

        private final Rectangle rect = new Rectangle(COLORMAP_WIDTH, COLORMAP_HEIGHT);

        public ColormapListCell() {
            super();
            setContentDisplay(ContentDisplay.LEFT);
        }

        @Override
        protected void updateItem(final ColorGradient gradient, final boolean empty) {
            super.updateItem(gradient, empty);
            if (gradient == null || empty) {
                setGraphic(null);
                setText("-");
            } else {
                rect.setFill(new LinearGradient(0, 0, COLORMAP_WIDTH, 0, false, CycleMethod.NO_CYCLE, gradient.getStops()));
                setGraphic(rect);
                setText(gradient.toString());
            }
        }
    }
}
