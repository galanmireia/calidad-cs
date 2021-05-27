package com.ibm.nmon.gui.main;

import java.awt.BorderLayout;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeEvent;

import javax.swing.event.TreeSelectionListener;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.tree.TreePath;

import com.ibm.nmon.gui.tree.TreePathParser;

import javax.swing.JFileChooser;
import javax.swing.JPanel;

import com.ibm.nmon.data.DataSet;
import com.ibm.nmon.data.DataType;

import com.ibm.nmon.gui.analysis.SummaryTablePanel;

import com.ibm.nmon.gui.chart.BaseChartPanel;

import com.ibm.nmon.gui.chart.annotate.AnnotationCache;
import com.ibm.nmon.gui.chart.annotate.AnnotationListener;

import com.ibm.nmon.gui.file.GUIFileChooser;

import com.ibm.nmon.gui.Styles;

/**
 * Central JPanel responsible for managing what is currently viewed in the UI. Listens for
 * <code>chartsDisplayed</code> property changes to display either charts or
 * {@link SummaryTablePanel}. The chart view will be one of {@link SummaryView}, {@link DataSetView}
 * or {@link DataTypeView} depending on the current tree path selected.
 */
public final class ViewManager extends JPanel implements PropertyChangeListener, TreeSelectionListener,
        AnnotationListener {
    private static final long serialVersionUID = 1411425971480047253L;

    private final NMONVisualizerGui gui;

    private final SummaryView summaryView;
    private final DataSetView dataSetView;
    private final DataTypeView dataTypeView;

    private final JPanel blank;

    private final SummaryTablePanel tablePanel;

    private final PathParser pathParser;

    private TreePath currentPath;

    private ChartSplitPane currentView;

    // if adding an annotation, ignore events from AnnotationCache
    private boolean addingAnnotation = false;

    // single use flag to make sure the ChartSplitPane is setup correctly
    // see PathParser.onReturn()
    private boolean displayedOnce = false;

    ViewManager(NMONVisualizerGui gui) {
        super(new BorderLayout());
        this.gui = gui;
        this.pathParser = new PathParser();

        summaryView = new SummaryView(gui);
        dataSetView = new DataSetView(gui);
        dataTypeView = new DataTypeView(gui);

        summaryView.addPropertyChangeListener("annotation", this);
        dataSetView.addPropertyChangeListener("annotation", this);
        dataTypeView.addPropertyChangeListener("annotation", this);

        summaryView.addPropertyChangeListener("chart", this);
        dataSetView.addPropertyChangeListener("chart", this);
        dataTypeView.addPropertyChangeListener("chart", this);

        blank = new JPanel();
        blank.setBackground(java.awt.Color.WHITE);
        blank.setBorder(Styles.createTopLineBorder(this));

        tablePanel = new SummaryTablePanel(gui, gui.getMainFrame());
        tablePanel.setEnabled(false);

        gui.addPropertyChangeListener("chartsDisplayed", this);

        AnnotationCache.addAnnotationListener(this);

        add(blank);
    }

    void displayTableColumnChooser() {
        if (currentView != null) {
            currentView.displayTableColumnChooser();
        }
    }

    private void showNothing() {
        if (getComponent(0) != blank) {
            remove(0);
            add(blank);

            summaryView.setEnabled(false);
            dataSetView.setEnabled(false);
            dataTypeView.setEnabled(false);
            tablePanel.setEnabled(false);

            updateDividerLocation();

            currentView = null;
        }
    }

    private void showSummaryView() {
        if (currentView != summaryView) {
            remove(0);
            add(summaryView);

            summaryView.setEnabled(true);
            dataSetView.setEnabled(false);
            dataTypeView.setEnabled(false);
            tablePanel.setEnabled(false);

            updateDividerLocation();

            currentView = summaryView;
        }
    }

    private void showDataSetView() {
        if (currentView != dataSetView) {
            remove(0);
            add(dataSetView);

            summaryView.setEnabled(false);
            dataSetView.setEnabled(true);
            dataTypeView.setEnabled(false);
            tablePanel.setEnabled(false);

            updateDividerLocation();

            currentView = dataSetView;
        }
    }

    private void showDataTypeView() {
        if (currentView != dataTypeView) {
            remove(0);
            add(dataTypeView);

            summaryView.setEnabled(false);
            dataSetView.setEnabled(false);
            dataTypeView.setEnabled(true);
            tablePanel.setEnabled(false);

            updateDividerLocation();

            currentView = dataTypeView;
        }
    }

    private void showTable() {
        if (getComponent(0) != tablePanel) {
            remove(0);
            add(tablePanel);

            summaryView.setEnabled(false);
            dataSetView.setEnabled(false);
            dataTypeView.setEnabled(false);
            tablePanel.setEnabled(true);

            updateDividerLocation();

            currentView = null;
        }
    }

    private void updateDividerLocation() {
        if (currentView != null) {
            int location = currentView.getDividerLocation();

            summaryView.setDividerLocation(location);
            dataSetView.setDividerLocation(location);
            dataTypeView.setDividerLocation(location);
        }
    }

    public void saveCharts() {
        if (currentView != null) {
            // note title here is consistent with BaseChartPanel.doSaveAs()
            // this dialog however only allows selecting the directory to save and uses the default
            // file names rather than allowing the user to specify a file name since a view may
            // contain multiple charts (i.e. a ReportPanel)
            GUIFileChooser chooser = new GUIFileChooser(gui, "Select Save Location");
            chooser.setFileSelectionMode(GUIFileChooser.DIRECTORIES_ONLY);

            if (chooser.showDialog(gui.getMainFrame(), "Save") == JFileChooser.APPROVE_OPTION) {
                String directory = chooser.getSelectedFile().getAbsolutePath();

                currentView.saveCharts(directory);
            }
        }
    }

    @Override
    public void propertyChange(PropertyChangeEvent evt) {
        if ("chartsDisplayed".equals(evt.getPropertyName())) {
            if ((Boolean) evt.getNewValue()) {
                pathParser.parse(currentPath);
            }
            else {
                showTable();
                // disabling the chart menu will be handled in MainMenu.propertyChange()
            }
        }
        else if ("chart".equals(evt.getPropertyName())) {
            if (AnnotationCache.hasAnnotations()) {
                if (evt.getNewValue() != null) {
                    addingAnnotation = true;
                    ((BaseChartPanel) evt.getNewValue()).addAnnotations(AnnotationCache.getAnnotations());
                    ((BaseChartPanel) evt.getNewValue()).addMarkers(AnnotationCache.getMarkers());
                    addingAnnotation = false;
                }
            }
        }
        else if ("annotation".equals(evt.getPropertyName())) {
            if ((evt.getNewValue() != null) && !addingAnnotation) {
                addingAnnotation = true;
                AnnotationCache.add(evt.getNewValue());
                addingAnnotation = false;
            }
        }
    }

    @Override
    public void annotationAdded() {
        annotationsCleared();
    }

    @Override
    public void annotationRemoved() {
        annotationsCleared();
    }

    @Override
    public void annotationsCleared() {
        if (!addingAnnotation) {
            if (currentView != null) {
                BaseChartPanel currentChart = currentView.getChartPanel();

                if (currentChart != null) {
                    addingAnnotation = true;
                    currentChart.addAnnotations(AnnotationCache.getAnnotations());
                    currentChart.addMarkers(AnnotationCache.getMarkers());
                    addingAnnotation = false;
                }
            }
        }
    }

    @Override
    public void valueChanged(TreeSelectionEvent e) {
        currentPath = e.getNewLeadSelectionPath();

        pathParser.parse(currentPath);
    }

    private final class PathParser extends TreePathParser {
        @Override
        protected void onNullPath() {
            if (gui.getBooleanProperty("chartsDisplayed")) {
                showNothing();
                enableChartMenu(false);
            }
        }

        @Override
        protected void onRootPath() {
            if (gui.getBooleanProperty("chartsDisplayed")) {
                if (gui.getDataSetCount() == 0) {
                    showNothing();
                }
                else {
                    showSummaryView();
                    enableChartMenu(true);
                }
            }
        }

        @Override
        protected void onDataSetPath(DataSet data) {
            dataSetView.setData(data);

            if (gui.getBooleanProperty("chartsDisplayed")) {
                showDataSetView();
                dataSetView.setEnabled(true);
                enableChartMenu(true);
            }
        }

        @Override
        protected void onProcessPath(DataSet data) {
            if (gui.getBooleanProperty("chartsDisplayed")) {
                showNothing();
                enableChartMenu(false);
            }
        }

        @Override
        protected void onTypePath(DataSet data, DataType type) {
            dataTypeView.setData(data, type);

            if (gui.getBooleanProperty("chartsDisplayed")) {
                showDataTypeView();
                enableChartMenu(true);
            }
        }

        @Override
        protected void onFieldPath(DataSet data, DataType type, String field) {
            dataTypeView.setData(data, type, field);

            if (gui.getBooleanProperty("chartsDisplayed")) {
                showDataTypeView();
                enableChartMenu(true);
            }
        }

        @Override
        protected Object onReturn(DataSet data, DataType type, String field) {
            // the first time a chart view is displayed,
            // set the proportional size of the scroll pane
            if (!displayedOnce && (currentView != null)) {
                // force layout to ensure currentView has a defined size
                // or setDividerLocation will not work
                doLayout();
                currentView.setDividerLocation(.75);

                displayedOnce = true;
            }

            repaint();

            return super.onReturn(data, type, field);
        }

        private void enableChartMenu(boolean enabled) {
            MainMenu menu = (MainMenu) gui.getMainFrame().getJMenuBar();

            menu.enableChartSubMenu(enabled);
        }
    }
}
