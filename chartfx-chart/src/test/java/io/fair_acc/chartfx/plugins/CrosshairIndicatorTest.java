package io.fair_acc.chartfx.plugins;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.fair_acc.chartfx.Chart;
import javafx.geometry.Point2D;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.text.Text;
import javafx.stage.Stage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.robot.Motion;

import io.fair_acc.chartfx.axes.spi.DefaultNumericAxis;
import io.fair_acc.chartfx.ui.utils.JavaFXInterceptorUtils.SelectiveJavaFxInterceptor;
import io.fair_acc.chartfx.ui.utils.TestFx;
import io.fair_acc.chartfx.utils.FXUtils;
import io.fair_acc.dataset.testdata.spi.SineFunction;

/**
 * Basic interface tests for CrosshairIndicatorTest plugin
 *
 * @author rstein
 */
@ExtendWith(ApplicationExtension.class)
@ExtendWith(SelectiveJavaFxInterceptor.class)
class CrosshairIndicatorTest {
    private final Pane boxLeft = new Pane();
    private Chart chart;

    @Start
    void start(Stage stage) {
        chart = new Chart();
        chart.getDatasets().add(new SineFunction("test function", 100));

        boxLeft.setStyle("-fx-background-color: red;");
        boxLeft.setMinWidth(100);
        HBox.setHgrow(boxLeft, Priority.ALWAYS);
        HBox.setHgrow(chart, Priority.ALWAYS);

        final HBox root = new HBox(boxLeft, chart);

        Scene scene = new Scene(root, 500, 400);
        stage.setScene(scene);
        stage.show();
    }

    @TestFx
    void attachPluginTests() {
        assertNotNull(chart);
        assertEquals(0, chart.getPlugins().size());
        final CrosshairIndicator plugin = new CrosshairIndicator();
        assertDoesNotThrow(() -> chart.getPlugins().add(plugin));
        assertEquals(1, chart.getPlugins().size());

        assertDoesNotThrow(() -> chart.getPlugins().remove(plugin));
        assertEquals(0, chart.getPlugins().size());
    }

    @Test
    void labelTestTests(FxRobot robot) {
        final CrosshairIndicator plugin = new CrosshairIndicator();
        assertDoesNotThrow(() -> FXUtils.runAndWait(() -> chart.getPlugins().add(plugin)));
        assertEquals(1, chart.getPlugins().size());
        System.err.println("pluging" + chart.getPlugins());
        Text label = plugin.coordinatesLabel;
        assertNotNull(label, "no crosshair label found");
        assertTrue(label.getText().isBlank(), "initial label being blank");
        assertFalse(plugin.getChartChildren().contains(label));

        robot.moveTo(chart, Motion.DEFAULT);
        assertTrue(plugin.getChartChildren().contains(label));
        assertTrue(plugin.getChartChildren().contains(label));
        final String text1 = label.getText();
        assertFalse(text1.isBlank());
        robot.moveBy(+100, 5);
        String text2 = label.getText();
        assertFalse(text2.isBlank());
        assertNotEquals(text1, text2);

        robot.moveBy(-200, 5);
        text2 = label.getText();
        assertFalse(text2.isBlank());
        assertNotEquals(text1, text2);

        robot.moveTo(chart, Pos.TOP_CENTER, new Point2D(0, +3), Motion.DEFAULT);
        robot.interrupt();
        text2 = label.getText();
        assertFalse(text2.isBlank());
        assertNotEquals(text1, text2);

        robot.moveTo(chart, Pos.BOTTOM_CENTER, new Point2D(0, -3), Motion.DEFAULT);
        robot.interrupt();
        text2 = label.getText();
        assertFalse(text2.isBlank());
        assertNotEquals(text1, text2);

        robot.moveTo(boxLeft, Motion.DEFAULT);
        final String text3 = label.getText();
        assertFalse(text3.isBlank());
        assertNotEquals(text2, text3);

        robot.interrupt();
        robot.moveBy(10, 10);
        robot.interrupt();
        final String text4 = label.getText();
        assertFalse(text4.isBlank());
        assertEquals(text3, text4);
    }
}
