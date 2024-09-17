package com.mubasher.dfix.license;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableModel;
import java.awt.*;
import java.text.SimpleDateFormat;
import java.util.Date;

public class DataCollector extends JFrame {
    private static final Logger logger = LogManager.getLogger(DataCollector.class);
    private static final String DATE_FORMAT = "yyyyMMdd";
    private static final String ADD_ROW_BUTTON_TIPS = "Press this to add a new Row to the Table.";
    private static final String GEN_BUTTON_TIPS = "Press this to genenrate the License File after fill the Table.";
    private static final String PAR_JTEXT_FIELD_TIPS = "No.of.Sessions allowed for a server. Mandatory Field. Minimum value = 1, Maximum value = 127.";
    private final String sesCountJTextFeildTips = "Starting Date of the License Period. Format : " + DATE_FORMAT.toUpperCase() + ". Mandatory if there is a license Period.";
    private static final String START_DATE_JTEXT_FIELD_TIPS = "Set this to support Parallel Instances for High Available Solutions. " +
            "Maximum value: 127, Default value = 1";
    private final String[] columns = {"#", "IP/HostName *", "Duration in Months"};
    private final String[] columnsTips = {"Row Id"
            , "IP/HostName for the target Server. Mandatory field. Loop Back values like 'Localhost/127.0.0.1 are not allowed.'"
            , "License period in Months from the start Date. Minimum value = 1, Default value = 12. Forever license can be given if the value is '*'"};
    private final DefaultTableModel tableModel;
    private final JTable inputTable;
    private final JButton generateButton;
    private final JButton addRowButton;
    private final Font defaultFontBold = new Font("Arial", Font.BOLD, 15);
    private final Font defaultFont = new Font("Arial", Font.PLAIN, 15);
    private boolean isLicenseGenInProgress = false;
    private final SimpleDateFormat simpleDateFormat = new SimpleDateFormat(DATE_FORMAT);
    private final JTextField parJTextFeild = new JTextField("1");
    private static final String PAR_J_TEXT_FIELD_NAME = "Parallel Instances";
    private final JTextField sesCountJTextFeild = new JTextField("1");
    private static final String SES_COUNT_J_TEXT_FIELD_NAME = "No.of.Sessions";
    private final JTextField startDateJTextFeild = new JTextField(simpleDateFormat.format(new Date()));


    private DataCollector() {
        super("DFIXRouter License Generator");
        tableModel = new DefaultTableModel(columns, 0);
        inputTable = getInputTable(tableModel);
        generateButton = getGenerateButton();
        addRowButton = getAddRowButton();
        initComponents();
    }

    public JButton getAddRowButton() {
        final JButton addRButton = getButton("Add New Row");
        addRButton.setToolTipText(ADD_ROW_BUTTON_TIPS);
        addRButton.addActionListener(evt -> {
            if (!isLicenseGenInProgress) {
                String[] row = {String.valueOf(tableModel.getRowCount()), "127.0.0.1", "12"};
                tableModel.addRow(row);
            }
        });
        return addRButton;
    }

    private JTable getInputTable(TableModel tableModel) {
        final JTable jTable = new JTable(tableModel);
        jTable.setDefaultEditor(String.class, new MyEditor());
        jTable.setFont(defaultFont);
        jTable.setShowVerticalLines(true);
        jTable.setShowHorizontalLines(true);
        jTable.setRowHeight(25);
        ToolTipHeader tooltipHeader = new ToolTipHeader(jTable.getColumnModel());
        tooltipHeader.setToolTipStrings(columnsTips);
        tooltipHeader.setFont(defaultFontBold);
        jTable.setTableHeader(tooltipHeader);
        return jTable;
    }

    public JButton getGenerateButton() {
        final JButton genLicenseButton = getButton("Generate");
        genLicenseButton.setToolTipText(GEN_BUTTON_TIPS);

        genLicenseButton.addActionListener(evt -> {
            if (!isLicenseGenInProgress) {
                isLicenseGenInProgress = true;
                GenerateButtonListener.getGenerateButtonListener().setjTable(inputTable);
                try {
                    String parallelInstance = parJTextFeild.getText();
                    String startDate = startDateJTextFeild.getText();
                    String sessionCount = sesCountJTextFeild.getText();
                    GenerateButtonListener.getGenerateButtonListener().setParallelInstances(parallelInstance);
                    GenerateButtonListener.getGenerateButtonListener().setStartDate(startDate);
                    GenerateButtonListener.getGenerateButtonListener().setSessionCount(sessionCount);
                    logger.debug("License creation Request: parallelInstance - {} startDate - {} sessionCount - {}", parallelInstance, startDate, sessionCount);
                    GenerateButtonListener.getGenerateButtonListener().generateLicenseFile();
                } catch (Exception e) {
                    JOptionPane.showMessageDialog(genLicenseButton, e.getMessage(),"Input Error", JOptionPane.ERROR_MESSAGE);

                } finally {
                    isLicenseGenInProgress = false;
                }
            }
        });

        return genLicenseButton;
    }

    private void initComponents() {
        setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        setVisible(true);

        final GroupLayout layout = new GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        final JScrollPane sp = new JScrollPane(inputTable);
        sp.setFont(defaultFont);

        parJTextFeild.setFont(defaultFont);
        parJTextFeild.setToolTipText(PAR_JTEXT_FIELD_TIPS);
        final JLabel parJlabel = new JLabel(PAR_J_TEXT_FIELD_NAME, SwingConstants.LEFT);
        parJlabel.setOpaque(true);
        parJlabel.setBackground(Color.WHITE);
        parJlabel.setForeground(Color.BLACK);
        parJlabel.setVerticalTextPosition(SwingConstants.CENTER);
        parJlabel.setFont(defaultFontBold);

        sesCountJTextFeild.setFont(defaultFont);
        sesCountJTextFeild.setToolTipText(sesCountJTextFeildTips);
        final JLabel sessCountJlabel = new JLabel(SES_COUNT_J_TEXT_FIELD_NAME, SwingConstants.LEFT);
        sessCountJlabel.setOpaque(true);
        sessCountJlabel.setBackground(Color.WHITE);
        sessCountJlabel.setForeground(Color.BLACK);
        sessCountJlabel.setVerticalTextPosition(SwingConstants.CENTER);
        sessCountJlabel.setFont(defaultFontBold);

        startDateJTextFeild.setFont(defaultFont);
        startDateJTextFeild.setToolTipText(START_DATE_JTEXT_FIELD_TIPS);
        final JLabel startDateJlabel = new JLabel("Start Date", SwingConstants.LEFT);
        startDateJlabel.setOpaque(true);
        startDateJlabel.setBackground(Color.WHITE);
        startDateJlabel.setForeground(Color.BLACK);
        startDateJlabel.setVerticalTextPosition(SwingConstants.CENTER);
        startDateJlabel.setFont(defaultFontBold);

        layout.setHorizontalGroup(layout.createParallelGroup(GroupLayout.Alignment.LEADING)
                .addGroup(layout.createSequentialGroup()
                        .addComponent(parJlabel, 250, 250, 250)
                        .addComponent(parJTextFeild, 250, 250, 250))
                .addGroup(layout.createSequentialGroup()
                        .addComponent(sessCountJlabel, 250, 250, 250)
                        .addComponent(sesCountJTextFeild, 250, 250, 250))
                .addGroup(layout.createSequentialGroup()
                        .addComponent(startDateJlabel, 250, 250, 250)
                        .addComponent(startDateJTextFeild, 250, 250, 250))
                .addComponent(addRowButton,GroupLayout.Alignment.LEADING, 250, 250, 250)
                .addComponent(sp)
                .addComponent(generateButton,GroupLayout.Alignment.LEADING, 250, 250, 250));
        layout.setVerticalGroup(layout.createParallelGroup(GroupLayout.Alignment.LEADING)
                .addGroup(layout.createSequentialGroup().addGap(10, 10, 10)
                        .addGroup(layout.createParallelGroup(GroupLayout.Alignment.LEADING)
                                .addComponent(parJlabel, GroupLayout.DEFAULT_SIZE, 30, 30)
                                .addComponent(parJTextFeild, GroupLayout.DEFAULT_SIZE, 30, 30))
                        .addGroup(layout.createParallelGroup(GroupLayout.Alignment.LEADING)
                                .addComponent(sessCountJlabel, GroupLayout.DEFAULT_SIZE, 30, 30)
                                .addComponent(sesCountJTextFeild, GroupLayout.DEFAULT_SIZE, 30, 30))
                        .addGroup(layout.createParallelGroup(GroupLayout.Alignment.LEADING)
                                .addComponent(startDateJlabel, GroupLayout.DEFAULT_SIZE, 30, 30)
                                .addComponent(startDateJTextFeild, GroupLayout.DEFAULT_SIZE, 30, 30))
                        .addComponent(addRowButton, GroupLayout.DEFAULT_SIZE, 30, 30)
                        .addComponent(sp)
                        .addComponent(generateButton, GroupLayout.DEFAULT_SIZE, 30, 30)));
        setBounds(0, 0, 500, 500);
        pack();
    }

    public static void main(String[] args) {
        try {
            for (UIManager.LookAndFeelInfo info : UIManager.getInstalledLookAndFeels())
                if ("Nimbus".equals(info.getName())) {
                    UIManager.setLookAndFeel(info.getClassName());
                    break;
                }
        } catch (ClassNotFoundException | InstantiationException | IllegalAccessException |
                 UnsupportedLookAndFeelException ex) {
            logger.error(ex.getMessage(), ex);
        }

        EventQueue.invokeLater(() -> new DataCollector().setVisible(true));
    }

    private JButton getButton(String text) {
        final JButton button = new JButton(text);
        button.setFont(defaultFontBold);
        return button;
    }

    public static String getDateFormat() {
        return DATE_FORMAT;
    }

    public static String getParJTextFieldName() {
        return PAR_J_TEXT_FIELD_NAME;
    }

    public static String getSesCountJTextFieldName() {
        return SES_COUNT_J_TEXT_FIELD_NAME;
    }
}
