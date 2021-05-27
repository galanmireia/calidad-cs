package com.ibm.nmon.gui.report;

import org.slf4j.Logger;

import javax.swing.JFrame;
import javax.swing.ButtonGroup;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JRadioButton;
import javax.swing.ListSelectionModel;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JSplitPane;
import javax.swing.JOptionPane;
import javax.swing.JFileChooser;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;

import java.awt.event.ActionListener;
import java.awt.event.ActionEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.event.ListSelectionEvent;

import java.beans.PropertyChangeListener;

import java.io.File;

import javax.swing.JPanel;

import javax.swing.border.EmptyBorder;

import com.ibm.nmon.data.DataSet;
import com.ibm.nmon.data.DataSetListener;

import com.ibm.nmon.gui.main.NMONVisualizerGui;
import com.ibm.nmon.gui.chart.BaseChartPanel;

import com.ibm.nmon.gui.chart.annotate.AnnotationCache;
import com.ibm.nmon.gui.chart.annotate.AnnotationListener;

import com.ibm.nmon.gui.file.GUIFileChooser;
import com.ibm.nmon.gui.Styles;

/**
 * Window for displaying a custom set of charts. Includes a list of processed datasets (systems) on the left and a chart
 * on the right.
 */
public final class ReportFrame extends JFrame implements DataSetListener, PropertyChangeListener, AnnotationListener {
    private static final long serialVersionUID = -2870624156130798498L;

    private static final Logger LOGGER = org.slf4j.LoggerFactory.getLogger(ReportFrame.class);

    private final NMONVisualizerGui gui;

    private ReportSplitPane reportSplitPane;

    private final JList<DataSet> systems;

    // if adding an annotation, ignore events from AnnotationCache
    private boolean addingAnnotation = false;

    public ReportFrame(NMONVisualizerGui gui) {
        super("Custom Report");

        this.gui = gui;
        this.reportSplitPane = new ReportSplitPane(gui, this);
        this.reportSplitPane.addPropertyChangeListener(this);

        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setResizable(true);
        setIconImage(Styles.REPORT_ICON.getImage());

        // Centered in the main frame at 90% of its size
        Dimension parentSize = gui.getMainFrame().getSize();
        Dimension thisSize = new Dimension((int) (parentSize.getWidth() * 0.9), (int) (parentSize.getHeight() * 0.9));
        setSize(thisSize);

        setLocationRelativeTo(gui.getMainFrame());

        // maximize if main frame is also maximized
        // size and location will still be set and remembered as above
        if ((gui.getMainFrame().getExtendedState() & JFrame.MAXIMIZED_BOTH) == JFrame.MAXIMIZED_BOTH) {
            setExtendedState(getExtendedState() | JFrame.MAXIMIZED_BOTH);
        }

        setJMenuBar(new ReportMenu(gui, this));

        ReportSystemsListModel model = new ReportSystemsListModel();

        systems = new JList<DataSet>(model);
        systems.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        systems.setCellRenderer(new DefaultListCellRenderer() {
            private static final long serialVersionUID = -5095717717027686318L;

            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected,
                    boolean cellHasFocus) {

                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);

                // see ReportSystemsListModel, which manages the indexing
                if (index == 0) {
                    setText("All Systems");
                    setIcon(Styles.REPORT_ICON);
                }
                else {
                    setText(((DataSet) value).getHostname());
                    setIcon(Styles.COMPUTER_ICON);
                }

                setBorder(new EmptyBorder(2, 5, 2, 5));

                return this;
            }
        });

        systems.addListSelectionListener(new ListSelectionListener() {
            @Override
            public void valueChanged(ListSelectionEvent e) {
                if (!e.getValueIsAdjusting()) {
                    DataSet selected = systems.getModel()
                            .getElementAt(systems.getSelectionModel().getMinSelectionIndex());

                    if (selected == null) {
                        reportSplitPane.setData(ReportFrame.this.gui.getDataSets());

                        enableMultiplexing(false);
                    }
                    else {
                        reportSplitPane.setData(java.util.Collections.singletonList(selected));

                        enableMultiplexing(true);
                    }
                }
            }
        });

        for (DataSet data : gui.getDataSets()) {
            model.addData(data);
        }

        JRadioButton none = new JRadioButton("None");
        JRadioButton byType = new JRadioButton("By Type");
        JRadioButton byField = new JRadioButton("By Field");

        none.setActionCommand(ReportPanel.MultiplexMode.NONE.name());
        byType.setActionCommand(ReportPanel.MultiplexMode.BY_TYPE.name());
        byField.setActionCommand(ReportPanel.MultiplexMode.BY_FIELD.name());

        none.setFont(Styles.LABEL);
        byType.setFont(Styles.LABEL);
        byField.setFont(Styles.LABEL);

        none.setBorder(Styles.CONTENT_BORDER);
        byType.setBorder(Styles.CONTENT_BORDER);
        byField.setBorder(Styles.CONTENT_BORDER);

        ActionListener modeChanger = new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                reportSplitPane.setMultiplexMode(ReportPanel.MultiplexMode.valueOf(e.getActionCommand()));
            }
        };

        none.addActionListener(modeChanger);
        byType.addActionListener(modeChanger);
        byField.addActionListener(modeChanger);

        none.setSelected(true);

        ButtonGroup group = new ButtonGroup();
        group.add(none);
        group.add(byType);
        group.add(byField);

        JLabel multiplex = new JLabel("Chart Multiplexing:");
        multiplex.setFont(Styles.LABEL);

        JPanel top = new JPanel();
        top.add(multiplex);
        top.add(none);
        top.add(byType);
        top.add(byField);

        JPanel right = new JPanel(new BorderLayout());

        right.add(top, BorderLayout.PAGE_START);
        right.add(reportSplitPane, BorderLayout.CENTER);

        // tree of parsed files on the left, content on the left
        JSplitPane lrSplitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        // never resize list automatically
        lrSplitPane.setResizeWeight(0);
        lrSplitPane.setBorder(null);

        lrSplitPane.setLeftComponent(systems);
        lrSplitPane.setRightComponent(right);

        setContentPane(lrSplitPane);

        systems.setSelectedIndex(0);
        enableMultiplexing(false);

        gui.addDataSetListener(this);
        AnnotationCache.addAnnotationListener(this);

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowOpened(WindowEvent e) {
                // can only update the divider locations when the window is visible
                // list gets 15%; charts get 70%
                ((JSplitPane) getContentPane()).setDividerLocation(.15);
                reportSplitPane.setDividerLocation(.7);
            }
        });
    }

    @Override
    public void dataAdded(DataSet data) {
        if (systems.getSelectedIndex() == 0) {
            // update All Systems chart
            reportSplitPane.setData(gui.getDataSets());
        }

        ((ReportSystemsListModel) systems.getModel()).addData(data);
    }

    @Override
    public void dataRemoved(DataSet data) {
        if (systems.getSelectedIndex() == 0) {
            // update All Systems chart
            reportSplitPane.setData(gui.getDataSets());
        }
        else {
            if (systems.getSelectedValue().equals(data)) {
                // data removed, back to All Systems
                systems.setSelectedIndex(0);
            }
        }

        ((ReportSystemsListModel) systems.getModel()).removeData(data);
    }

    @Override
    public void dataChanged(DataSet data) {
        ((ReportSystemsListModel) systems.getModel()).dataChanged();
    }

    @Override
    public void dataCleared() {
        if (systems.getSelectedIndex() == 0) {
            // update All Systems chart
            reportSplitPane.setData(gui.getDataSets());
        }
        else {
            systems.setSelectedIndex(0);
        }

        ((ReportSystemsListModel) systems.getModel()).clearData();
    }

    private void enableMultiplexing(boolean enable) {
        JSplitPane lrSplitPane = (JSplitPane) getContentPane();
        JPanel top = (JPanel) ((JPanel) lrSplitPane.getRightComponent()).getComponent(0);

        for (int i = 1; i < 4; i++) {
            top.getComponent(i).setEnabled(enable);
        }
    }

    @Override
    public void dispose() {
        reportSplitPane.dispose();

        gui.removeDataSetListener(this);
        AnnotationCache.removeAnnoationListener(this);

        super.dispose();
    }

    public void propertyChange(java.beans.PropertyChangeEvent evt) {
        if ("chart".equals(evt.getPropertyName())) {
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
            BaseChartPanel currentChart = reportSplitPane.getChartPanel();
            if (currentChart != null) {
                addingAnnotation = true;
                currentChart.addAnnotations(AnnotationCache.getAnnotations());
                currentChart.addMarkers(AnnotationCache.getMarkers());
                addingAnnotation = false;
            }
        }
    }

    NMONVisualizerGui getGui() {
        return gui;
    }

    boolean loadReportDefinition(File reportFile) {
        if (!reportFile.exists()) {
            JOptionPane.showMessageDialog(this, "File '" + reportFile.getName() + "' is not a valid file",
                    "Invalid File", JOptionPane.ERROR_MESSAGE);

            // existing report => true
            return reportSplitPane.hasReport();
        }

        try {
            reportSplitPane.loadReport(reportFile);

            setTitle("Custom Report" + " - " + reportFile.getName());

            configureReport();

            return true;
        }
        catch (Exception e) {
            setTitle("Custom Report");

            LOGGER.error("could not parse report file '{}'", reportFile.getAbsolutePath(), e);
            JOptionPane.showMessageDialog(this, "Error parsing '" + reportFile.getName() + "'\n" + e.getMessage(),
                    "Parse Error", JOptionPane.ERROR_MESSAGE);

            // reportSplitPane.clearReport();

            return false;
        }
    }

    void loadDefaultDataSetReport() {
        reportSplitPane.loadDefaultDataSetReport();

        setTitle("Default DataSet Report");

        configureReport();
    }

    void loadDefaultSummaryReport() {
        reportSplitPane.loadDefaultSummaryReport();

        setTitle("Default All Systems Report");

        configureReport();
    }

    void loadDefaultIOStatReport() {
        reportSplitPane.loadDefaultIOStatReport();
        setTitle("Default IOStat Report");

        configureReport();
    }

    void loadDefaultIOStatDiskDataReport() {
        reportSplitPane.loadDefaultIOStatDiskDataReport();

        setTitle("Default IOStat Disk Data Report");

        configureReport();
    }

    private void configureReport() {
        // set the report's data
        DataSet selected = systems.getModel().getElementAt(systems.getSelectionModel().getMinSelectionIndex());

        if (selected == null) {
            reportSplitPane.setData(gui.getDataSets());
        }
        else {
            reportSplitPane.setData(java.util.Collections.singletonList(selected));
        }
    }

    void saveAllCharts() {
        GUIFileChooser chooser = new GUIFileChooser(gui, "Select Save Location");
        chooser.setFileSelectionMode(GUIFileChooser.DIRECTORIES_ONLY);
        chooser.setMultiSelectionEnabled(false);

        if (chooser.showDialog(this, "Save") == JFileChooser.APPROVE_OPTION) {
            String directory = chooser.getSelectedFile().getAbsolutePath();

            reportSplitPane.saveCharts(directory);
        }
    }
}
