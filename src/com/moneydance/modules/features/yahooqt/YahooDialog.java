/*************************************************************************\
* Copyright (C) 2010 The Infinite Kind, LLC
*
* This code is released as open source under the Apache 2.0 License:<br/>
* <a href="http://www.apache.org/licenses/LICENSE-2.0">
* http://www.apache.org/licenses/LICENSE-2.0</a><br />
\*************************************************************************/

package com.moneydance.modules.features.yahooqt;

import com.moneydance.apps.md.controller.FeatureModuleContext;
import com.moneydance.apps.md.controller.UserPreferences;
import com.moneydance.apps.md.controller.Util;
import com.moneydance.apps.md.model.CurrencyTable;
import com.moneydance.apps.md.model.time.TimeInterval;
import com.moneydance.apps.md.view.gui.MoneydanceGUI;
import com.moneydance.apps.md.view.gui.OKButtonListener;
import com.moneydance.apps.md.view.gui.OKButtonPanel;
import com.moneydance.awt.GridC;
import com.moneydance.awt.JDateField;
import com.moneydance.util.StringUtils;
import com.moneydance.util.UiUtil;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.DefaultCellEditor;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.border.Border;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableColumnModel;
import javax.swing.table.JTableHeader;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumn;
import javax.swing.table.TableColumnModel;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagLayout;
import java.awt.Point;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.HierarchyEvent;
import java.awt.event.HierarchyListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.ArrayList;
import java.util.List;

/**
 * Main settings configuration dialog for the extension.
 *
 * @author Kevin Menningen - Mennē Software Solutions, LLC
 */
public class YahooDialog
  extends JDialog
  implements PropertyChangeListener
{
  private final JPanel contentPane = new JPanel(new BorderLayout(5, 5));
  private JButton _buttonNow;
  private JButton _buttonTest;
  private final JTable _table = new JTable();
  /** This contains data that is edited in the table. */
  private final StockQuotesModel _model;
  private final ResourceProvider _resources;
  private final IExchangeEditor _exchangeEditor = new ExchangeEditor();

  private JComboBox _historyConnectionSelect;
  private JComboBox _currentConnectionSelect;
  private JComboBox _ratesConnectionSelect;
  private IntervalChooser _intervalSelect;
  private JDateField _nextDate;
  private JCheckBox _saveCurrentInHistory = new JCheckBox();
  private ItemListCellRenderer _tableRenderer;
  private final JCheckBox _showZeroBalance = new JCheckBox();
  private final JLabel _testStatus = new JLabel();
  private boolean _okButtonPressed = false;

  public YahooDialog(final FeatureModuleContext context, final ResourceProvider resources,
                     final StockQuotesModel model) {
    super();
    _model = model;
    _model.addPropertyChangeListener(this);
    _resources = resources;
    initUI(context);
    setContentPane(contentPane);
    setModal(true);
    setTitle(resources.getString(L10NStockQuotes.SETTINGS_TITLE));
//    setIconImage(Main.getIcon()); // available in Java 1.6 only
    Dimension size = _model.getPreferences().getSizeSetting(N12EStockQuotes.SIZE_KEY);
    if (size.width == 0) {
      pack();
    } else {
      setSize(size);
    }
    Point location = _model.getPreferences().getXYSetting(N12EStockQuotes.LOCATION_KEY, -1, -1);
    if (location.x == -1) {
      Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
      location.x = (screenSize.width - getWidth()) / 2;
      location.y = (screenSize.height - getHeight()) / 2;
    }
    setLocation(location);
  }

  @Override
  public void setVisible(boolean visible) {
    if (visible) {
      _model.buildSecurityMap();
      setSecurityTableColumnSizes();
      validate();
    }
    super.setVisible(visible);
  }

  public boolean userAcceptedChanges() { return _okButtonPressed; }

  private void initUI(final FeatureModuleContext context) {
    JPanel fieldPanel = new JPanel(new GridBagLayout());
    fieldPanel.setBorder(BorderFactory.createEmptyBorder(UiUtil.DLG_VGAP, UiUtil.DLG_HGAP,
                                                         UiUtil.DLG_VGAP, UiUtil.DLG_HGAP));
    // pick which URL schemes to connect to (or no connection to disable the download)
    setupConnectionSelectors();
    setupIntervalSelector();
    _saveCurrentInHistory.setSelected(_model.getSaveCurrentAsHistory());
    // first column
    fieldPanel.add(new JLabel(SQUtil.getLabelText(_resources, L10NStockQuotes.RATES_CONNECTION)),
            GridC.getc(0, 0).label());
    fieldPanel.add(_ratesConnectionSelect,   GridC.getc(1, 0).field());
    fieldPanel.add(new JLabel(SQUtil.getLabelText(_resources, L10NStockQuotes.HISTORY_CONNECTION)),
            GridC.getc(0, 1).label());
    fieldPanel.add(_historyConnectionSelect, GridC.getc(1, 1).field());
    fieldPanel.add(new JLabel(SQUtil.getLabelText(_resources, L10NStockQuotes.CURRENT_CONNECTION)),
            GridC.getc(0, 2).label());
    fieldPanel.add(_currentConnectionSelect, GridC.getc(1, 2).field());
    // gap in middle
    fieldPanel.add(Box.createHorizontalStrut(UiUtil.DLG_HGAP), GridC.getc(2, 0));
    // second column
    fieldPanel.add(new JLabel(SQUtil.getLabelText(_resources, L10NStockQuotes.FREQUENCY_LABEL)),
            GridC.getc(3, 0).label());
    fieldPanel.add(_intervalSelect, GridC.getc(4, 0).field());
    fieldPanel.add(new JLabel(SQUtil.getLabelText(_resources, L10NStockQuotes.NEXT_DATE_LABEL)),
            GridC.getc(3, 1).label());
    fieldPanel.add(_nextDate, GridC.getc(4, 1).field());
    _saveCurrentInHistory.setText(_resources.getString(L10NStockQuotes.SAVE_CURRENT_OPTION));
    fieldPanel.add(_saveCurrentInHistory, GridC.getc(3, 2).colspan(2).field());
    // gap between the fields and the table
    fieldPanel.add(Box.createVerticalStrut(UiUtil.VGAP), GridC.getc(0, 3));
    // setup the table
    JScrollPane tableHost = setupSecurityTable();
    fieldPanel.add(tableHost, GridC.getc(0, 4).colspan(5).wxy(1,1).fillboth());
    _showZeroBalance.setText(_model.getGUI().getStr("show_zero_bal_accts"));
    fieldPanel.add(_showZeroBalance, GridC.getc(0, 5).colspan(5).field());
    _testStatus.setHorizontalAlignment(JLabel.CENTER);
    _testStatus.setText(" ");
    fieldPanel.add(_testStatus, GridC.getc(0, 6).colspan(5).field());
    fieldPanel.setBorder(BorderFactory.createEmptyBorder(UiUtil.DLG_VGAP, UiUtil.DLG_HGAP,
            0, UiUtil.DLG_HGAP));
    contentPane.add(fieldPanel, BorderLayout.CENTER);
    // buttons at bottom
    _buttonNow = new JButton(_model.getGUI().getStr("update"));
    _buttonTest = new JButton(_resources.getString(L10NStockQuotes.TEST));
    JPanel extraButtonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, UiUtil.HGAP, UiUtil.VGAP));
    extraButtonPanel.add(_buttonTest);
    extraButtonPanel.add(_buttonNow);
    // the built-in OK/Cancel buttons
    OKButtonPanel okButtons = new OKButtonPanel(_model.getGUI(), new DialogOKButtonListener(),
                                                OKButtonPanel.QUESTION_OK_CANCEL);
    JPanel bottomPanel = new JPanel(new BorderLayout());
    bottomPanel.setBorder(BorderFactory.createEmptyBorder(UiUtil.VGAP, UiUtil.DLG_HGAP,
                                                          UiUtil.DLG_VGAP, UiUtil.DLG_HGAP));
    bottomPanel.add(extraButtonPanel, BorderLayout.WEST);
    bottomPanel.add(okButtons, BorderLayout.CENTER);
    contentPane.add(bottomPanel, BorderLayout.SOUTH);
    // setup actions for the controls
    addActions(context);
  }

  private void addActions(final FeatureModuleContext context) {
    _showZeroBalance.addItemListener(new ItemListener() {
      public void itemStateChanged(ItemEvent e) {
        _model.getTableModel().setShowZeroBalance(e.getStateChange() == ItemEvent.SELECTED);
      }
    });
    _buttonNow.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        // Store what we have in the dialog - same as OK. We need to do this because the main app
        // update is called, which reads these settings from preferences or the data file.
        saveControlsToSettings();
        // listen for events so our status updates just like the main application's
        _model.addPropertyChangeListener(YahooDialog.this);
        // call the main update method
        context.showURL("moneydance:fmodule:yahooqt:update");
      }
    });
    _buttonTest.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        // save the selected connections into our model
        saveSelectedConnections();
        // store what we have into the symbol map
        _model.getTableModel().save();
        // listen for update events
        _model.addPropertyChangeListener(YahooDialog.this);
        _model.runDownloadTest();
      }
    });
  }

  private void setupConnectionSelectors() {
    // stock historical quotes
    _historyConnectionSelect = new JComboBox(
            _model.getConnectionList(BaseConnection.HISTORY_SUPPORT));
    _historyConnectionSelect.setSelectedItem(_model.getSelectedHistoryConnection());
    // current stock price
    _currentConnectionSelect = new JComboBox(
            _model.getConnectionList(BaseConnection.CURRENT_PRICE_SUPPORT));
    _currentConnectionSelect.setSelectedItem(_model.getSelectedCurrentPriceConnection());
    // currency exchange rates
    _ratesConnectionSelect = new JComboBox(
            _model.getConnectionList(BaseConnection.EXCHANGE_RATES_SUPPORT));
    _ratesConnectionSelect.setSelectedItem(_model.getSelectedExchangeRatesConnection());
  }

  private void setupIntervalSelector() {
    _intervalSelect = new IntervalChooser(_model.getGUI());
    final String paramStr = _model.getPreferences().getSetting(Main.UPDATE_INTERVAL_KEY, "");
    _intervalSelect.selectFromParams(paramStr);
    _nextDate = new JDateField(_model.getPreferences().getShortDateFormatter());
    loadNextDate();
  }

  private void loadNextDate() {
    int lastDate = _model.getRootAccount().getIntParameter(Main.QUOTE_LAST_UPDATE_KEY, 0);
    final int nextDate;
    if (lastDate == 0) {
      nextDate = Util.getStrippedDateInt(); // today
    } else {
      TimeInterval frequency = Main.getUpdateFrequency(_model.getPreferences());
      nextDate = SQUtil.getNextDate(lastDate, frequency);
    }
    _nextDate.setDateInt(nextDate);
  }


  private JScrollPane setupSecurityTable() {
    _table.setModel(_model.getTableModel());
    _table.setBorder(BorderFactory.createEmptyBorder(0, UiUtil.HGAP, 0, UiUtil.HGAP));
    JScrollPane host = new JScrollPane(_table, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
            JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);

    // consistent row height with other tables in the application
    _table.setRowHeight(_table.getRowHeight() + 8);
    _table.setAutoResizeMode(JTable.AUTO_RESIZE_LAST_COLUMN);
    _table.setRowSelectionAllowed(true);
    _table.setColumnSelectionAllowed(false);
    _table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    final TableColumnModel columnModel = createColumnModel();
    _table.setColumnModel(columnModel);
    _table.setDragEnabled(false);
//    _table.setFillsViewportHeight(true); // available in Java 1.6 only
    _table.setShowGrid(false);
    final JTableHeader tableHeader = new JTableHeader(columnModel);
    // the only way to get mouse clicks is to attach a listener to the header
    tableHeader.addMouseListener(new MouseAdapter() {
      @Override
      public void mouseClicked(MouseEvent event) {
        final JTableHeader header = (JTableHeader) event.getSource();
        TableColumnModel columnModel = header.getColumnModel();
        int viewColumn = header.columnAtPoint(event.getPoint());
        int column = columnModel.getColumn(viewColumn).getModelIndex();
        if (column == SecuritySymbolTableModel.USE_COL) {
          toggleIncludeAll();
        } else if (column == SecuritySymbolTableModel.EXCHANGE_COL) {
          batchChangeExchange();
        }
      }
    });
    tableHeader.setReorderingAllowed(false);
    _table.setTableHeader(tableHeader);
    _table.setDefaultRenderer(TableColumn.class, _tableRenderer);
    _table.addMouseListener(new MouseAdapter() {
      @Override
      public void mouseClicked(MouseEvent event) {
        // if the user clicks on a test result cell and there's a tooltip to show, put the tooltip
        // in a message dialog.
        TableColumnModel columnModel = _table.getColumnModel();
        int viewColumn = _table.columnAtPoint(event.getPoint());
        int column = columnModel.getColumn(viewColumn).getModelIndex();
        int row = _table.rowAtPoint(event.getPoint());
        if (column == SecuritySymbolTableModel.TEST_COL) {
          String message = _model.getTableModel().getToolTip(row, SecuritySymbolTableModel.TEST_COL);
          if (!SQUtil.isBlank(message)) {
            JPanel p = new JPanel(new GridBagLayout());
            String symbolTip = _model.getTableModel().getToolTip(row, SecuritySymbolTableModel.SYMBOL_COL);
            p.add(new JLabel(symbolTip), GridC.getc(0, 0));
            p.add(Box.createVerticalStrut(UiUtil.VGAP),  GridC.getc(0, 1));
            p.add(new JLabel(message), GridC.getc(0, 2));
            JOptionPane.showMessageDialog(YahooDialog.this, p);
          }
        }
      }
    });
    return host;
  }

  private void toggleIncludeAll() {
    // determine if any of the 'use' values is false, and if so the action will be to turn all on
    final boolean turnAllOff = _model.getTableModel().allSymbolsEnabled();
    _model.getTableModel().enableAllSymbols(!turnAllOff);
  }

  private void batchChangeExchange() {
    final ExchangeComboTableColumn.ComboListModel comboListModel =
            new ExchangeComboTableColumn.ComboListModel(getExchangeItems());
    final JComboBox exchangeCombo = new JComboBox(comboListModel);
    if (showField(exchangeCombo)) {
      StockExchange selected = (StockExchange)exchangeCombo.getSelectedItem();
      _model.getTableModel().batchChangeExchange(selected);
    }
  }

  private boolean showField(JComponent field) {
    JPanel p = new JPanel(new GridBagLayout());
    final MoneydanceGUI mdGUI = _model.getGUI();
    String msg = mdGUI.getStr("batch_msg");
    String fieldName = _resources.getString(L10NStockQuotes.EXCHANGE_TITLE);
    msg = StringUtils.replaceAll(msg, "{field}", fieldName);

    p.add(new JLabel(UiUtil.addLabelSuffix(mdGUI, msg)), GridC.getc(0,0));
    p.add(field, GridC.getc(1,0).wx(1).fillx());
    p.add(Box.createHorizontalStrut(120),  GridC.getc(1,1));

    // Requests focus on the combo box.
    field.addHierarchyListener(new HierarchyListener() {
      public void hierarchyChanged(HierarchyEvent e) {
        final Component c = e.getComponent();
        if (c.isShowing() && (e.getChangeFlags() &
        HierarchyEvent.SHOWING_CHANGED) != 0) {
          Window toplevel = SwingUtilities.getWindowAncestor(c);
          toplevel.addWindowFocusListener(new WindowAdapter() {
            public void windowGainedFocus(WindowEvent e) {
              c.requestFocusInWindow();
            }
          });
        }
      }
    });

    int result = JOptionPane.showConfirmDialog(this, p, mdGUI.getStr("batch_change"),
                                              JOptionPane.OK_CANCEL_OPTION,
                                              JOptionPane.QUESTION_MESSAGE);

    return result==JOptionPane.OK_OPTION;
  }

  private TableColumnModel createColumnModel() {
    final DefaultTableColumnModel columnModel = new DefaultTableColumnModel();
    _tableRenderer = new ItemListCellRenderer(_model.getGUI());

    TableColumn col;
    columnModel.addColumn(col = new TableColumn(SecuritySymbolTableModel.USE_COL, 20,
            new UseColumnRenderer(_model.getGUI()), new UseColumnEditor()));
    // special renderer allows the header to act like a checkbox to select all / deselect all
    UseColumnRenderer useHeaderRenderer = new UseColumnRenderer(_model.getGUI());
    col.setHeaderRenderer(useHeaderRenderer);
    col.setHeaderValue(" ");
    useHeaderRenderer.setIsHeaderCell();

    columnModel.addColumn(col = new TableColumn(SecuritySymbolTableModel.NAME_COL, 150,
            new SecurityNameCellRenderer(_model.getGUI()), null));
    col.setHeaderValue(_model.getGUI().getStr("curr_type_sec"));
    columnModel.addColumn(col = new TableColumn(SecuritySymbolTableModel.SYMBOL_COL, 40,
                                                _tableRenderer,
                                                new TickerColumnEditor()));
    col.setHeaderValue(_model.getGUI().getStr("currency_ticker"));

    // the stock exchange picker
    ExchangeComboTableColumn exchangeColumn =
            new ExchangeComboTableColumn(_model.getGUI(),
                                         SecuritySymbolTableModel.EXCHANGE_COL, 60,
                                         getExchangeItems(), _exchangeEditor, true);
    columnModel.addColumn(exchangeColumn);
    exchangeColumn.setHeaderValue(_resources.getString(L10NStockQuotes.EXCHANGE_TITLE));
    columnModel.addColumn(col = new TableColumn(SecuritySymbolTableModel.TEST_COL, 40,
            _tableRenderer, null));
    col.setHeaderValue(_resources.getString(L10NStockQuotes.TEST_TITLE));

    return columnModel;
  }

  private StockExchange[] getExchangeItems() {
    // set the name to a displayable localized string
    StockExchange.DEFAULT.setName(_model.getGUI().getStr("default"));
    // find all of the stock exchange items that have a currency that exists in the data file
    List<StockExchange> items = new ArrayList<StockExchange>();
    items.add(StockExchange.DEFAULT);
    final CurrencyTable ctable = _model.getRootAccount().getCurrencyTable();
    for (StockExchange exchange : _model.getExchangeList().getFullList()) {
      if (isValidExchange(ctable, exchange)) items.add(exchange);
    }
    return items.toArray(new StockExchange[items.size()]);
  }

  /**
   * Determine if we should show an exchange or not, depending upon whether the currency for that
   * exchange is defined in the data file or not.
   * @param ctable   The currency table from the data file.
   * @param exchange The stock exchange to test.
   * @return True if the stock exchange can be used, false if the currency does not exist.
   */
  private static boolean isValidExchange(CurrencyTable ctable, StockExchange exchange) {
    final String currencyId = exchange.getCurrencyCode();
    return (ctable.getCurrencyByIDString(currencyId) != null);
  }

  /**
   * Define the table column sizes according to the data in them.
   */
  private void setSecurityTableColumnSizes()
  {
    if ((_model.getTableModel() == null) || (_model.getTableModel().getRowCount() == 0)) return; // nothing to do
    // find the maximum width of the columns - there may be more columns in the model than in the view
    final int viewColumnCount = _table.getColumnModel().getColumnCount();
    int[] widths = new int[viewColumnCount];
    for (int column = 0; column < viewColumnCount; column++) {
      for (int row = 0; row < _table.getRowCount(); row++) {
        TableCellRenderer renderer = _table.getCellRenderer(row, column);
        Component comp = renderer.getTableCellRendererComponent(_table,
                _table.getValueAt(row, column), false, false, row, column);
        widths[column] = Math.max(widths[column], comp.getPreferredSize().width);
        if ((row == 0) && (column > 0)) {
          // include the header text too, but only for columns other than 'use'
          comp = renderer.getTableCellRendererComponent(_table, _model.getTableModel().getColumnName(column),
                  false, false, row, column);
          widths[column] = Math.max(widths[column], comp.getPreferredSize().width);
        }
      }
    }
    // set the last column to be as big as the biggest column - all extra space should be given to
    // the last column
    int maxWidth = 0;
    for (int width1 : widths) maxWidth = Math.max(width1, maxWidth);
    widths[SecuritySymbolTableModel.TEST_COL] = maxWidth;
    final TableColumnModel columnModel = _table.getColumnModel();
    for (int column = 0; column < widths.length; column++) {
      columnModel.getColumn(column).setPreferredWidth(widths[column]);
    }
  }

  private void onOK() {
    saveControlsToSettings();

    _okButtonPressed = true;
    dispose();
  }

  private void saveControlsToSettings() {
    saveSelectedConnections();
    _model.setSaveCurrentAsHistory(_saveCurrentInHistory.isSelected());
    // these are stored in preferences and are not file-specific
    UserPreferences prefs = _model.getPreferences();
    prefs.setSetting(Main.AUTO_UPDATE_KEY, isAnyConnectionSelected());
    prefs.setSetting(Main.UPDATE_INTERVAL_KEY, _intervalSelect.getSelectedInterval().getConfigKey());
    saveNextUpdateDate();
    // check if any of the settings that are stored in the specific data file have been changed
    if (_model.isDirty()) {
      _model.saveSettings();
      // mark that we made changes to the file
      _model.getRootAccount().accountModified(_model.getRootAccount());
    }
  }

  private void saveNextUpdateDate() {
    if (_model.getRootAccount() == null) return;
    int nextDate = _nextDate.getDateInt();
    // work backwards to get the calculated 'last update date'
    TimeInterval frequency = _intervalSelect.getSelectedInterval();
    _model.setHistoryDaysFromFrequency(frequency);
    int lastDate = SQUtil.getPreviousDate(nextDate, frequency);
    if (_model.isStockPriceSelected()) {
      _model.getRootAccount().setParameter(Main.QUOTE_LAST_UPDATE_KEY, lastDate);
    }
    if (_model.isExchangeRateSelected()) {
      _model.getRootAccount().setParameter(Main.RATE_LAST_UPDATE_KEY, lastDate);
    }
  }

  private void saveSelectedConnections() {
    _model.setSelectedHistoryConnection((BaseConnection)_historyConnectionSelect.getSelectedItem());
    _model.setSelectedCurrentPriceConnection((BaseConnection)_currentConnectionSelect.getSelectedItem());
    _model.setSelectedExchangeRatesConnection((BaseConnection)_ratesConnectionSelect.getSelectedItem());
  }

  private boolean isAnyConnectionSelected() {
    return _model.isStockPriceSelected() || _model.isExchangeRateSelected();
  }

  public void dispose() {
    _model.removePropertyChangeListener(this);
    _model.getPreferences().setXYSetting(N12EStockQuotes.LOCATION_KEY, getLocation());
    _model.getPreferences().setSizeSetting(N12EStockQuotes.SIZE_KEY, getSize());
    super.dispose();
  }

  public void propertyChange(PropertyChangeEvent event) {
    final String name = event.getPropertyName();
    if (N12EStockQuotes.STATUS_UPDATE.equals(name)) {
      final String status = (String) event.getNewValue();
      _testStatus.setText(status == null ? " " : status);
    } else if (N12EStockQuotes.DOWNLOAD_BEGIN.equals(name)) {
      final String text = _model.getGUI().getStr("cancel");
      UiUtil.runOnUIThread(new Runnable() {
        public void run() {
          _buttonTest.setText(text);
        }
      });
    } else if (N12EStockQuotes.DOWNLOAD_END.equals(name)) {
      final String text = _resources.getString(L10NStockQuotes.TEST);
      UiUtil.runOnUIThread(new Runnable() {
        public void run() {
          _buttonTest.setText(text);
          // the next update date may have changed now
          loadNextDate();
        }
      });
      // we're done listening for results
      _model.removePropertyChangeListener(this);
    } else if (N12EStockQuotes.HEADER_UPDATE.equals(name)) {
      _table.getTableHeader().repaint();
    }

  }

  private static class UseColumnRenderer extends JCheckBox implements TableCellRenderer {
    private static final Border noFocusBorder = new EmptyBorder(1, 1, 1, 1);
    private final MoneydanceGUI _mdGui;
    private boolean _isHeaderCell = false;

    public UseColumnRenderer(final MoneydanceGUI mdGui) {
      super();
      _mdGui = mdGui;
      setHorizontalAlignment(JLabel.CENTER);
      setBorderPainted(true);
    }

    public Component getTableCellRendererComponent(JTable table, Object value,
                                                   boolean isSelected, boolean hasFocus,
                                                   int row, int column) {
      if (_isHeaderCell) {
        final JTableHeader header = table.getTableHeader();
        if (header != null) {
          setForeground(header.getForeground());
          setBackground(header.getBackground());
          setFont(header.getFont());
        }
        setBorder(UIManager.getBorder("TableHeader.cellBorder"));
        setSelected(((SecuritySymbolTableModel)table.getModel()).allSymbolsEnabled());
      } else {
        if (isSelected) {
          setForeground(table.getSelectionForeground());
          setBackground(table.getSelectionBackground());
        } else {
          setForeground(table.getForeground());
          if (row % 2 == 0) {
            setBackground(_mdGui.getColors().homePageBG);
          } else {
            setBackground(_mdGui.getColors().homePageAltBG);
          }
        }
        setSelected((value instanceof Boolean) && ((Boolean) value).booleanValue());
        if (hasFocus) {
          setBorder(UIManager.getBorder("Table.focusCellHighlightBorder"));
        } else {
          setBorder(noFocusBorder);
        }
      }
      return this;
    }

    void setIsHeaderCell() {
      _isHeaderCell = true;
    }
  }

  private static class UseColumnEditor extends DefaultCellEditor {
    public UseColumnEditor() {
      super(new JCheckBox());
      JCheckBox checkBox = (JCheckBox) getComponent();
      checkBox.setHorizontalAlignment(JCheckBox.CENTER);
    }
  }

  /**
   * Cell renderer for the list, colors items that are in-use.
   */
  private class ItemListCellRenderer extends DefaultTableCellRenderer {
    private final MoneydanceGUI _mdGui;

    ItemListCellRenderer(final MoneydanceGUI mdGui) {
      _mdGui = mdGui;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Component getTableCellRendererComponent(JTable table, Object value,
                                                   boolean isSelected, boolean hasFocus,
                                                   int row, int column) {
      JComponent result = (JComponent) super.getTableCellRendererComponent(table, value,
              isSelected, hasFocus, row, column);

      // the unselected background alternates color for ease of distinction
      if (!isSelected) {
        if (row % 2 == 0) {
          setBackground(_mdGui.getColors().homePageBG);
        } else {
          setBackground(_mdGui.getColors().homePageAltBG);
        }
      }

      // in case the text is cut off, show complete text in a tool tip
      if ((column == SecuritySymbolTableModel.SYMBOL_COL) ||
              (column == SecuritySymbolTableModel.TEST_COL)) {
        result.setToolTipText(_model.getTableModel().getToolTip(row, column));
      }
      else if (value instanceof String) {
        result.setToolTipText((String)value);
      }

      return result;
    }

  }

  /**
   * Cell renderer for the list, colors items that are in-use.
   */
  private class SecurityNameCellRenderer extends DefaultTableCellRenderer {
    private final MoneydanceGUI _mdGui;
    private final JPanel _renderer;
    private final JLabel _shareDisplay;

    SecurityNameCellRenderer(final MoneydanceGUI mdGui) {
      _mdGui = mdGui;
      _renderer = new JPanel(new BorderLayout());
      _shareDisplay = new JLabel();
      _shareDisplay.setOpaque(true);
      _renderer.add(this, BorderLayout.CENTER);
      _renderer.add(_shareDisplay, BorderLayout.EAST);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Component getTableCellRendererComponent(JTable table, Object value,
                                                   boolean isSelected, boolean hasFocus,
                                                   int row, int column) {
      super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
      final String shares = (String) table.getModel().getValueAt(row, SecuritySymbolTableModel.SHARES_COL);
      _shareDisplay.setText(shares + N12EStockQuotes.SPACE);
      // the unselected background alternates color for ease of distinction
      if (!isSelected) {
        if (row % 2 == 0) {
          setBackground(_mdGui.getColors().homePageBG);
          _shareDisplay.setBackground(_mdGui.getColors().homePageBG);
        } else {
          setBackground(_mdGui.getColors().homePageAltBG);
          _shareDisplay.setBackground(_mdGui.getColors().homePageAltBG);
        }
      }
      _shareDisplay.setForeground(Color.GRAY); // lighter text for the share balance
      // in case the text is cut off, show complete text in a tool tip
      if (value instanceof String) {
        setToolTipText((String)value);
      }
      return _renderer;
    }
  }

  /**
   * Cell editor for in-place editing
   */
  private class TickerColumnEditor extends DefaultCellEditor {
    private String _value;

    public TickerColumnEditor() {
      super(new JTextField());
      getComponent().setName("Table.editor");
    }

    @Override
    public boolean stopCellEditing() {
      _value = (String) super.getCellEditorValue();
      return super.stopCellEditing();
    }

    @Override
    public Component getTableCellEditorComponent(JTable table, Object value,
                                                 boolean isSelected,
                                                 int row, int column) {
      _value = null;
      JTextField editor = (JTextField) super.getTableCellEditorComponent(table, value,
              isSelected, row, column);
      editor.requestFocusInWindow();
      editor.selectAll();
      return editor;
    }

    @Override
    public Object getCellEditorValue() {
      return _value;
    }
  }

  private class DialogOKButtonListener implements OKButtonListener {
    public void buttonPressed(int buttonId) {
      if (buttonId == OKButtonPanel.ANSWER_OK) {
        onOK();
      } else {
        dispose();
      }
    }
  }

  private class ExchangeEditor implements IExchangeEditor {
    public boolean edit(final StockExchange exchange) {
      final MoneydanceGUI mdGui = _model.getGUI();
      if (StockExchange.DEFAULT.equals(exchange)) {
        // not editable
        final String message = _resources.getString(L10NStockQuotes.ERROR_DEFAULT_NOT_EDITABLE);
        SwingUtilities.invokeLater(new Runnable() {
          public void run() {
            mdGui.showErrorMessage(message);
          }
        });
        return false;
      }
      final StockExchangeList exchangeList = _model.getExchangeList();
      final JDialog owner = YahooDialog.this;
      SwingUtilities.invokeLater(new Runnable() {
        public void run() {
          ExchangeDialog dialog = new ExchangeDialog(owner, mdGui, _resources, exchange,
                  exchangeList);
          dialog.setVisible(true);
        }
      });
      return true;
    }
  }
}
