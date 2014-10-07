package org.jdownloader.extensions.schedulerV2.gui;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.lang.reflect.Array;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.JSpinner.DateEditor;
import javax.swing.SpinnerDateModel;
import javax.swing.SpinnerNumberModel;

import jd.gui.UserIO;
import jd.gui.swing.jdgui.views.settings.components.ComboBox;
import net.miginfocom.swing.MigLayout;

import org.appwork.storage.JSonStorage;
import org.appwork.swing.MigPanel;
import org.appwork.swing.components.ExtTextField;
import org.appwork.uio.CloseReason;
import org.appwork.utils.swing.SwingUtils;
import org.appwork.utils.swing.dialog.AbstractDialog;
import org.jdownloader.extensions.schedulerV2.actions.AbstractScheduleAction;
import org.jdownloader.extensions.schedulerV2.actions.IScheduleActionConfig;
import org.jdownloader.extensions.schedulerV2.helpers.ActionHelper;
import org.jdownloader.extensions.schedulerV2.helpers.ActionHelper.TIME_OPTIONS;
import org.jdownloader.extensions.schedulerV2.helpers.ActionHelper.WEEKDAY;
import org.jdownloader.extensions.schedulerV2.model.ScheduleEntry;
import org.jdownloader.extensions.schedulerV2.model.ScheduleEntryStorable;
import org.jdownloader.extensions.schedulerV2.translate.T;
import org.jdownloader.gui.translate._GUI;

public class AddScheduleEntryDialog extends AbstractDialog<ScheduleEntry> {

    private JPanel                                                  content;
    private ExtTextField                                            scheduleName;
    private MigPanel                                                timePane;
    private MigPanel                                                timeOptionPaneOnlyOnce;
    private JSpinner                                                timeSpinnerOnce;
    private JSpinner                                                dateSpinnerOnce;
    private JSpinner                                                minuteSpinnerHourly;
    private MigPanel                                                timeOptionPaneHourly;
    private MigPanel                                                timeOptionPaneDaily;
    private JSpinner                                                timeSpinnerDaily;
    private MigPanel                                                timeOptionPaneWeekly;
    private JSpinner                                                timeSpinnerWeekly;
    private JSpinner                                                hourSpinnerInterval;
    private MigPanel                                                timeOptionPaneInterval;
    private JSpinner                                                minuteSpinnerInterval;
    private ComboBox<TIME_OPTIONS>                                  intervalBox;
    private ComboBox<AbstractScheduleAction<IScheduleActionConfig>> actionBox;
    private MigPanel                                                actionParameterPanel;

    private ScheduleEntry                                           editEntry = null;
    private MigPanel                                                timeOptionPaneSpecificDays;
    private JCheckBox                                               specificDaysMon;
    private JCheckBox                                               specificDaysTue;
    private JCheckBox                                               specificDaysWed;
    private JCheckBox                                               specificDaysThu;
    private JCheckBox                                               specificDaysFri;
    private JCheckBox                                               specificDaysSat;
    private JCheckBox                                               specificDaysSun;
    private JSpinner                                                timeSpinnerSpecificDays;

    public AddScheduleEntryDialog() {
        super(UserIO.NO_ICON, T._.addScheduleEntryDialog_title(), null, _GUI._.lit_save(), null);
    }

    public AddScheduleEntryDialog(ScheduleEntry entry) {
        super(UserIO.NO_ICON, T._.addScheduleEntryDialog_title_edit(), null, _GUI._.lit_save(), null);
        this.editEntry = entry;
    }

    @Override
    protected ScheduleEntry createReturnValue() {
        if (!getCloseReason().equals(CloseReason.OK)) {
            return null;
        }

        ScheduleEntryStorable actionStorable = new ScheduleEntryStorable();

        actionStorable.setEnabled(true);
        actionStorable.setName(scheduleName.getText());

        AbstractScheduleAction<IScheduleActionConfig> action = actionBox.getSelectedItem();
        actionStorable.setActionID(action.getActionID());
        actionStorable.setActionConfig(JSonStorage.toString(action.getConfig()));

        TIME_OPTIONS timeType = intervalBox.getSelectedItem();
        switch (timeType) {
        case HOURLY: {
            Date d = (Date) minuteSpinnerHourly.getValue();
            Calendar timestamp = Calendar.getInstance();
            timestamp.set(Calendar.SECOND, 0);
            timestamp.set(Calendar.MINUTE, d.getMinutes());
            actionStorable.setTimestamp(timestamp.getTimeInMillis() / 1000l);
        }
            break;
        case DAILY: {
            Date d = (Date) timeSpinnerDaily.getValue();
            Calendar timestamp = Calendar.getInstance();
            timestamp.set(Calendar.SECOND, 0);
            timestamp.set(Calendar.MINUTE, d.getMinutes());
            timestamp.set(Calendar.HOUR_OF_DAY, d.getHours());
            actionStorable.setTimestamp(timestamp.getTimeInMillis() / 1000l);
        }
            break;
        case SPECIFICDAYS: {
            Date d = (Date) timeSpinnerSpecificDays.getValue();
            Calendar timestamp = Calendar.getInstance();
            timestamp.set(Calendar.SECOND, 0);
            timestamp.set(Calendar.MINUTE, d.getMinutes());
            timestamp.set(Calendar.HOUR_OF_DAY, d.getHours());
            actionStorable.setTimestamp(timestamp.getTimeInMillis() / 1000l);
            // TODO: Days
            LinkedList<WEEKDAY> days = new LinkedList<ActionHelper.WEEKDAY>();
            if (specificDaysMon.isSelected()) {
                days.add(WEEKDAY.MONDAY);
            }
            if (specificDaysTue.isSelected()) {
                days.add(WEEKDAY.TUESDAY);
            }
            if (specificDaysWed.isSelected()) {
                days.add(WEEKDAY.WEDNESDAY);
            }
            if (specificDaysThu.isSelected()) {
                days.add(WEEKDAY.THURSDAY);
            }
            if (specificDaysFri.isSelected()) {
                days.add(WEEKDAY.FRIDAY);
            }
            if (specificDaysSat.isSelected()) {
                days.add(WEEKDAY.SATURDAY);
            }
            if (specificDaysSun.isSelected()) {
                days.add(WEEKDAY.SUNDAY);
            }
            actionStorable._setSelectedDays(days);
        }
            break;
        case WEEKLY: {
            Date d = (Date) timeSpinnerWeekly.getValue();
            Calendar timestamp = Calendar.getInstance();
            timestamp.set(Calendar.SECOND, 0);
            timestamp.set(Calendar.MINUTE, d.getMinutes());
            timestamp.set(Calendar.HOUR_OF_DAY, d.getHours());
            int dayOfWeek;

            timestamp.set(Calendar.DAY_OF_WEEK, d.getDay() + 1);
            actionStorable.setTimestamp(timestamp.getTimeInMillis() / 1000l);
        }
            break;
        case CHOOSEINTERVAL: {
            actionStorable.setTimestamp(Calendar.getInstance().getTimeInMillis() / 1000l);
            actionStorable.setIntervalHour((Integer) hourSpinnerInterval.getValue());
            actionStorable.setIntervalMin((Integer) minuteSpinnerInterval.getValue());
        }
            break;
        case ONLYONCE:
        default: {
            Date time = (Date) timeSpinnerOnce.getValue();
            Date date = (Date) dateSpinnerOnce.getValue();
            Calendar timestamp = Calendar.getInstance();
            timestamp.set(date.getYear() + 1900, date.getMonth(), date.getDate(), time.getHours(), time.getMinutes());
            timestamp.set(Calendar.SECOND, 0);
            actionStorable.setTimestamp(timestamp.getTimeInMillis() / 1000l);
        }
            break;
        }
        actionStorable._setTimeType(timeType);
        try {
            return new ScheduleEntry(actionStorable);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }

    }

    @Override
    public JComponent layoutDialogContent() {
        MigPanel migPanel = new MigPanel("ins 0 0 5 0, wrap 2", "[][grow,fill]", "[sg name][sg header][sg repeat][sg parameter][sg header][sg action][sg parameter2, 26]");
        migPanel.setOpaque(false);

        migPanel.add(new JLabel(T._.scheduleTable_column_name() + ":"));
        scheduleName = new ExtTextField();
        scheduleName.setText(editEntry != null ? editEntry.getName() : T._.addScheduleEntryDialog_defaultScheduleName());

        migPanel.add(scheduleName, "");

        migPanel.add(header(T._.addScheduleEntryDialog_header_time()), "spanx, growx,pushx,newline 15");
        migPanel.add(new JLabel(T._.addScheduleEntryDialog_repeat() + ":"), "gapleft 10");

        ArrayList<TIME_OPTIONS> values = new ArrayList<ActionHelper.TIME_OPTIONS>(Arrays.asList(ActionHelper.TIME_OPTIONS.values()));
        values.remove(ActionHelper.TIME_OPTIONS.DAILY);
        values.remove(ActionHelper.TIME_OPTIONS.WEEKLY);
        intervalBox = new ComboBox<TIME_OPTIONS>(values.toArray(new ActionHelper.TIME_OPTIONS[values.size()])) {

            @Override
            protected String getLabel(int index, TIME_OPTIONS value) {
                return value.getReadableName();
            }
        };

        intervalBox.addActionListener(new ActionListener() {

            @Override
            public void actionPerformed(ActionEvent e) {
                ComboBox cb = (ComboBox) e.getSource();
                Object selected = cb.getSelectedItem();
                selectTimeOptionPane((TIME_OPTIONS) selected);
            }
        });
        if (editEntry != null) {
            intervalBox.setSelectedItem(editEntry.getTimeType());
        } else {
            intervalBox.setSelectedIndex(0);
        }
        migPanel.add(intervalBox, "");

        // Begin time subpanels
        timePane = new MigPanel("", "", "");
        setupTimeOptionPanes();
        selectTimeOptionPane(editEntry != null ? editEntry.getTimeType() : ActionHelper.TIME_OPTIONS.ONLYONCE);
        migPanel.add(timePane, "spanx, growx");

        // Begin action area
        migPanel.add(header(T._.addScheduleEntryDialog_actionParameters()), "spanx, growx,newline 15");
        migPanel.add(new JLabel(T._.scheduleTable_column_action() + ":"), "gapleft 10");

        AbstractScheduleAction<IScheduleActionConfig>[] array = (AbstractScheduleAction<IScheduleActionConfig>[]) Array.newInstance(AbstractScheduleAction.class, ActionHelper.ACTIONS.size());
        AbstractScheduleAction<IScheduleActionConfig> selectedItem = ActionHelper.ACTIONS.get(0);
        for (int i = 0; i < ActionHelper.ACTIONS.size(); i++) {
            if (editEntry != null && editEntry.getAction() != null && editEntry.getAction().getActionID().equals(ActionHelper.ACTIONS.get(i).getActionID())) {
                selectedItem = editEntry.getAction();
                array[i] = selectedItem;
            } else {
                array[i] = ActionHelper.ACTIONS.get(i);
            }

        }
        actionBox = new ComboBox<AbstractScheduleAction<IScheduleActionConfig>>(array) {
            @Override
            protected String getLabel(int index, AbstractScheduleAction<IScheduleActionConfig> value) {
                return value.getReadableName();
            }
        };
        actionBox.addActionListener(new ActionListener() {

            @Override
            public void actionPerformed(ActionEvent e) {
                selectActionConfigPanel(actionBox.getSelectedItem().getConfigPanel());
            }
        });

        migPanel.add(actionBox);
        actionParameterPanel = new MigPanel("ins 0", "", "");
        migPanel.add(actionParameterPanel, "spanx, growx, pushx");

        actionBox.setSelectedItem(selectedItem);

        content = migPanel;
        updatePanel();
        loadEntry(editEntry);
        return content;
    }

    private void loadEntry(ScheduleEntry editEntry) {
        if (editEntry == null) {
            scheduleName.setText(T._.addScheduleEntryDialog_defaultScheduleName());

        } else {
            scheduleName.setText(editEntry.getName());
        }
    }

    private void selectActionConfigPanel(JPanel configPanel) {

        if (actionParameterPanel == null) {
            return;
        }
        actionParameterPanel.removeAll();
        actionParameterPanel.setLayout(new MigLayout("ins 0 10 0 0", "", ""));
        if (configPanel != null) {
            actionParameterPanel.add(configPanel, "growx,pushx");
        }
        actionParameterPanel.repaint();
        pack();
    }

    private void setupTimeOptionPanes() {
        timeOptionPaneOnlyOnce = new MigPanel(new MigLayout("ins 0,wrap 2", "[sg 1][sg 2,grow,fill]", "[sg 1][sg 1]"));
        dateSpinnerOnce = new JSpinner(new SpinnerDateModel());
        dateSpinnerOnce.setEditor(new DateEditor(dateSpinnerOnce, ((SimpleDateFormat) SimpleDateFormat.getDateInstance()).toPattern()));
        dateSpinnerOnce.setValue(editEntry != null && editEntry.getTimeType().equals(TIME_OPTIONS.ONLYONCE) ? new Date(editEntry.getTimestamp() * 1000l) : new Date());
        timeSpinnerOnce = new JSpinner(new SpinnerDateModel());
        timeSpinnerOnce.setEditor(new DateEditor(timeSpinnerOnce, ((SimpleDateFormat) DateFormat.getTimeInstance(DateFormat.SHORT)).toPattern()));
        timeSpinnerOnce.setValue(editEntry != null && editEntry.getTimeType().equals(TIME_OPTIONS.ONLYONCE) ? new Date(editEntry.getTimestamp() * 1000l) : new Date());
        timeOptionPaneOnlyOnce.add(new JLabel(T._.addScheduleEntryDialog_date() + ":"));
        timeOptionPaneOnlyOnce.add(dateSpinnerOnce, "");
        timeOptionPaneOnlyOnce.add(new JLabel(T._.addScheduleEntryDialog_time() + ":"));
        timeOptionPaneOnlyOnce.add(timeSpinnerOnce, "");
        timeOptionPaneOnlyOnce.setVisible(false);

        timeOptionPaneHourly = new MigPanel(new MigLayout("ins 0,wrap 2", "[sg 1][sg 2,grow,fill]", "[sg 1][sg 1]"));
        minuteSpinnerHourly = new JSpinner(new SpinnerDateModel());
        minuteSpinnerHourly.setEditor(new DateEditor(minuteSpinnerHourly, "mm"));
        minuteSpinnerHourly.setValue(editEntry != null && editEntry.getTimeType().equals(TIME_OPTIONS.HOURLY) ? new Date(editEntry.getTimestamp() * 1000l) : new Date());
        timeOptionPaneHourly.add(new JLabel(T._.addScheduleEntryDialog_minute() + ":"));
        timeOptionPaneHourly.add(minuteSpinnerHourly, "");
        timeOptionPaneHourly.setVisible(false);

        timeOptionPaneDaily = new MigPanel(new MigLayout("ins 0,wrap 2", "[sg 1][sg 2,grow,fill]", "[sg 1][sg 1]"));
        timeSpinnerDaily = new JSpinner(new SpinnerDateModel());
        timeSpinnerDaily.setEditor(new DateEditor(timeSpinnerDaily, ((SimpleDateFormat) DateFormat.getTimeInstance(DateFormat.SHORT)).toPattern()));
        timeSpinnerDaily.setValue(editEntry != null && editEntry.getTimeType().equals(TIME_OPTIONS.DAILY) ? new Date(editEntry.getTimestamp() * 1000l) : new Date());
        timeOptionPaneDaily.add(new JLabel(T._.addScheduleEntryDialog_time() + ":"));
        timeOptionPaneDaily.add(timeSpinnerDaily);
        timeOptionPaneDaily.setVisible(false);

        timeOptionPaneSpecificDays = new MigPanel(new MigLayout("ins 0,wrap 7", "[grow]", ""));

        MigPanel specificDaysSubPanel = new MigPanel("ins 0, wrap 2", "[][grow,fill]", "");
        specificDaysSubPanel.add(new JLabel(T._.addScheduleEntryDialog_time() + ":"));
        timeSpinnerSpecificDays = new JSpinner(new SpinnerDateModel());
        timeSpinnerSpecificDays.setEditor(new DateEditor(timeSpinnerSpecificDays, ((SimpleDateFormat) DateFormat.getTimeInstance(DateFormat.SHORT)).toPattern()));
        timeSpinnerSpecificDays.setValue(editEntry != null && editEntry.getTimeType().equals(TIME_OPTIONS.SPECIFICDAYS) ? new Date(editEntry.getTimestamp() * 1000l) : new Date());
        specificDaysSubPanel.add(timeSpinnerSpecificDays);
        timeOptionPaneSpecificDays.add(specificDaysSubPanel, "spanx, growx, pushx");

        List<WEEKDAY> days = (editEntry == null) ? null : editEntry.getSelectedDays();
        timeOptionPaneSpecificDays.add(specificDaysMon = new JCheckBox(WEEKDAY.MONDAY.getReadableName(), days == null ? true : days.contains(WEEKDAY.MONDAY)));
        timeOptionPaneSpecificDays.add(specificDaysTue = new JCheckBox(WEEKDAY.TUESDAY.getReadableName(), days == null ? true : days.contains(WEEKDAY.TUESDAY)));
        timeOptionPaneSpecificDays.add(specificDaysWed = new JCheckBox(WEEKDAY.WEDNESDAY.getReadableName(), days == null ? true : days.contains(WEEKDAY.WEDNESDAY)));
        timeOptionPaneSpecificDays.add(specificDaysThu = new JCheckBox(WEEKDAY.THURSDAY.getReadableName(), days == null ? true : days.contains(WEEKDAY.THURSDAY)));
        timeOptionPaneSpecificDays.add(specificDaysFri = new JCheckBox(WEEKDAY.FRIDAY.getReadableName(), days == null ? true : days.contains(WEEKDAY.FRIDAY)));
        timeOptionPaneSpecificDays.add(specificDaysSat = new JCheckBox(WEEKDAY.SATURDAY.getReadableName(), days == null ? true : days.contains(WEEKDAY.SATURDAY)));
        timeOptionPaneSpecificDays.add(specificDaysSun = new JCheckBox(WEEKDAY.SUNDAY.getReadableName(), days == null ? true : days.contains(WEEKDAY.SUNDAY)));

        timeOptionPaneSpecificDays.setVisible(false);

        timeOptionPaneWeekly = new MigPanel(new MigLayout("ins 0,wrap 2", "", ""));
        timeSpinnerWeekly = new JSpinner(new SpinnerDateModel());
        timeSpinnerWeekly.setEditor(new DateEditor(timeSpinnerWeekly, "E, " + ((SimpleDateFormat) DateFormat.getTimeInstance(DateFormat.SHORT)).toPattern()));
        timeSpinnerWeekly.setValue(editEntry != null && editEntry.getTimeType().equals(TIME_OPTIONS.WEEKLY) ? new Date(editEntry.getTimestamp() * 1000l) : new Date());
        timeOptionPaneWeekly.add(new JLabel(T._.addScheduleEntryDialog_time() + ":"), "growx, width 18%");
        timeOptionPaneWeekly.add(timeSpinnerWeekly, "growx, width 30%");
        timeOptionPaneWeekly.setVisible(false);

        timeOptionPaneInterval = new MigPanel(new MigLayout("ins 0,wrap 2", "[sg 1][sg 2,grow,fill]", "[sg 1][sg 1]"));
        hourSpinnerInterval = new JSpinner(new SpinnerNumberModel(editEntry != null && editEntry.getTimeType().equals(TIME_OPTIONS.CHOOSEINTERVAL) ? editEntry.getIntervalHour() : 1, 0, 365 * 24, 1));
        timeOptionPaneInterval.add(new JLabel(T._.addScheduleEntryDialog_hours() + ":"), "");
        timeOptionPaneInterval.add(hourSpinnerInterval);
        minuteSpinnerInterval = new JSpinner(new SpinnerNumberModel(editEntry != null && editEntry.getTimeType().equals(TIME_OPTIONS.CHOOSEINTERVAL) ? editEntry.getIntervalMinunte() : 0, 0, 59, 1));
        timeOptionPaneInterval.add(new JLabel(T._.addScheduleEntryDialog_minutes() + ":"), "");
        timeOptionPaneInterval.add(minuteSpinnerInterval, "");
        timeOptionPaneInterval.setVisible(false);
    }

    private void selectTimeOptionPane(TIME_OPTIONS inteval) {
        if (timeOptionPaneOnlyOnce == null) {
            return;
        }
        timePane.removeAll();
        timePane.setLayout(new MigLayout("ins 0 10 0 0", "", ""));

        switch (inteval) {
        case HOURLY:
            timePane.add(timeOptionPaneHourly, "pushx, growx");
            timeOptionPaneHourly.setVisible(true);
            break;
        case DAILY:
            timePane.add(timeOptionPaneDaily, "pushx, growx");
            timeOptionPaneDaily.setVisible(true);
            break;
        case SPECIFICDAYS:
            timePane.add(timeOptionPaneSpecificDays, "pushx, growx");
            timeOptionPaneSpecificDays.setVisible(true);
            break;
        case WEEKLY:
            timePane.add(timeOptionPaneWeekly, "pushx, growx");
            timeOptionPaneWeekly.setVisible(true);
            break;
        case CHOOSEINTERVAL:
            timePane.add(timeOptionPaneInterval, "pushx, growx");
            timeOptionPaneInterval.setVisible(true);
            break;
        case ONLYONCE:
        default:
            timePane.add(timeOptionPaneOnlyOnce, "pushx, growx");
            timeOptionPaneOnlyOnce.setVisible(true);
            break;
        }
        timePane.repaint();
        // updatePanel();
    }

    @Override
    protected int getPreferredWidth() {
        if (content == null) {
            return 430;
        }
        return (int) content.getMinimumSize().getWidth() + 20;
    }

    protected void updatePanel() {
        if (content == null) {
            return;
        }
        // TODO check enable "ok" button
        getDialog().pack();
    }

    private void checkOK() {
        this.okButton.setEnabled(scheduleName.getText().length() > 0 || true); // TODO -> conditions
    }

    private JComponent header(String caption) {
        JLabel ret = SwingUtils.toBold(new JLabel(caption));
        ret.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, ret.getForeground()));
        return ret;
    }

    @Override
    protected void packed() {
        super.packed();

    }
}