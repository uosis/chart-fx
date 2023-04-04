package io.fair_acc.chartfx.renderer.spi.financial.css;

import io.fair_acc.chartfx.Chart;

public interface FinancialColorSchemeAware {
    /**
     * Apply theme to the whole chart domain object and attached renders. The renders have to be present before
     * applying this theme process.
     *
     * @param theme             selected theme, according to FinancialColorScheme or your inherited theme classes.
     * @param customColorScheme custom color schemes for selected theme (customization simplification), if null theme color scheme is used
     * @param chart             prepared chart for visualization.
     * @throws Exception if processing fails
     */
    void applyTo(String theme, String customColorScheme, Chart chart) throws Exception;

    /**
     * Apply theme to the whole chart domain object and attached renders. The renders have to be present before
     * applying this theme process.
     *
     * @param theme selected theme, according to FinancialColorScheme or your inherited theme classes.
     * @param chart prepared chart for visualization.
     * @throws Exception if processing fails
     */
    void applyTo(String theme, Chart chart) throws Exception;
}
