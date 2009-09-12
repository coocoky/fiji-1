package fiji.updater.ui;

import fiji.updater.Updater;

import fiji.updater.logic.Installer;
import fiji.updater.logic.PluginCollection;
import fiji.updater.logic.PluginObject;
import fiji.updater.logic.PluginObject.Action;
import fiji.updater.logic.PluginObject.Status;
import fiji.updater.logic.PluginUploader;

import fiji.updater.util.Downloader;
import fiji.updater.util.Canceled;
import fiji.updater.util.Progress;
import fiji.updater.util.Util;

import ij.IJ;
import ij.Prefs;

import ij.gui.GenericDialog;

import java.awt.Dimension;
import java.awt.TextField;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

import java.io.File;
import java.io.IOException;

import java.util.ArrayList;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.JTextPane;

import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.event.TableModelEvent;
import javax.swing.event.TableModelListener;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;

public class UpdaterFrame extends JFrame
		implements TableModelListener, ListSelectionListener {
	PluginCollection plugins;
	long xmlLastModified;

	private JFrame loadedFrame;
	private JTextField txtSearch;
	private ViewOptions viewOptions;
	private PluginTable table;
	private JLabel lblPluginSummary;
	private PluginDetails pluginDetails;
	private JButton btnStart;

	//For developers
	// TODO: no more Hungarian notation
	private JButton btnUpload;
	private JButton btnEditDetails;

	public UpdaterFrame() {
		super("Plugin Manager");

		plugins = PluginCollection.getInstance();

		setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
		addWindowListener(new WindowAdapter() {
			public void windowClosing(WindowEvent e) {
				quit();
			}
		});

		//======== Start: LEFT PANEL ========
		JPanel leftPanel = SwingTools.verticalPanel();
		txtSearch = new JTextField();
		txtSearch.getDocument().addDocumentListener(new DocumentListener() {

			public void changedUpdate(DocumentEvent e) {
				updatePluginsTable();
			}

			public void removeUpdate(DocumentEvent e) {
				updatePluginsTable();
			}

			public void insertUpdate(DocumentEvent e) {
				updatePluginsTable();
			}
		});
		SwingTools.labelComponent("Search:", txtSearch, leftPanel);
		leftPanel.add(Box.createRigidArea(new Dimension(0,10)));

		viewOptions = new ViewOptions();
		viewOptions.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				updatePluginsTable();
			}
		});
	
		SwingTools.labelComponent("View Options:", viewOptions, leftPanel);
		leftPanel.add(Box.createRigidArea(new Dimension(0,10)));

		//Create labels to annotate table
		SwingTools.label("Please choose what you want to install/uninstall:", leftPanel);
		leftPanel.add(Box.createRigidArea(new Dimension(0,5)));

		//Label text for plugin summaries
		lblPluginSummary = new JLabel();
		JPanel lblSummaryPanel = SwingTools.horizontalPanel();
		lblSummaryPanel.add(lblPluginSummary);
		lblSummaryPanel.add(Box.createHorizontalGlue());

		//Create the plugin table and set up its scrollpane
		table = new PluginTable(plugins);
		table.getSelectionModel().addListSelectionListener(this);
		JScrollPane pluginListScrollpane = new JScrollPane(table);
		pluginListScrollpane.getViewport().setBackground(table.getBackground());

		leftPanel.add(pluginListScrollpane);
		leftPanel.add(Box.createRigidArea(new Dimension(0,5)));
		leftPanel.add(lblSummaryPanel);
		//======== End: LEFT PANEL ========

		//======== Start: RIGHT PANEL ========
		// TODO: do we really want to win the "Who can make the longest function names?" contest?
		JPanel rightPanel = SwingTools.verticalPanel();

		rightPanel.add(Box.createVerticalGlue());
		if (Util.isDeveloper) {
			JPanel editButtonPanel = SwingTools.horizontalPanel();
			editButtonPanel.add(Box.createHorizontalGlue());
			btnEditDetails = SwingTools.button("Edit Details",
					"Edit selected plugin's details", new ActionListener() {
				public void actionPerformed(ActionEvent e) {
					clickToEditDescriptions();
				}
			}, editButtonPanel);
			btnEditDetails.setEnabled(false);
			rightPanel.add(editButtonPanel);
		}

		pluginDetails = new PluginDetails();
		SwingTools.tab(pluginDetails, "Details",
				"Individual Plugin information",
				350, 315, rightPanel);
		// TODO: put this into SwingTools, too
		rightPanel.add(Box.createRigidArea(new Dimension(0,25)));
		//======== End: RIGHT PANEL ========

		//======== Start: TOP PANEL (LEFT + RIGHT) ========
		JPanel topPanel = SwingTools.horizontalPanel();
		topPanel.add(leftPanel);
		topPanel.add(Box.createRigidArea(new Dimension(15,0)));
		topPanel.add(rightPanel);
		topPanel.setBorder(BorderFactory.createEmptyBorder(20, 15, 5, 15));
		//======== End: TOP PANEL (LEFT + RIGHT) ========

		//======== Start: BOTTOM PANEL ========
		JPanel bottomPanel = SwingTools.horizontalPanel();
		bottomPanel.setBorder(BorderFactory.createEmptyBorder(5, 15, 15, 15));
		bottomPanel.add(new PluginAction("Keep as-is", null));
		bottomPanel.add(Box.createRigidArea(new Dimension(15,0)));
		bottomPanel.add(new PluginAction("Install", Action.INSTALL,
			"Update", Action.UPDATE));
		bottomPanel.add(Box.createRigidArea(new Dimension(15,0)));
		bottomPanel.add(new PluginAction("Remove", Action.REMOVE));

		bottomPanel.add(Box.createHorizontalGlue());

		//Button to start actions
		btnStart = SwingTools.button("Apply changes",
				"Start installing/uninstalling plugins", new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				clickToBeginOperations();
			}
		}, bottomPanel);
		btnStart.setEnabled(false);

		//includes button to upload to server if is a Developer using
		if (Util.isDeveloper) {
			bottomPanel.add(Box.createRigidArea(new Dimension(15,0)));
			btnUpload = SwingTools.button("Upload to server",
					"Upload selected plugins to server", new ActionListener() {
				public void actionPerformed(ActionEvent e) {
					upload();
				}
			}, bottomPanel);
			btnUpload.setEnabled(false);
		}

		bottomPanel.add(Box.createRigidArea(new Dimension(15,0)));
		SwingTools.button("Close", "Exit Plugin Manager",
			new ActionListener() {
				public void actionPerformed(ActionEvent e) {
					quit();
				}
			}, bottomPanel);
		//======== End: BOTTOM PANEL ========

		getContentPane().setLayout(new BoxLayout(getContentPane(),
					BoxLayout.Y_AXIS));
		getContentPane().add(topPanel);
		getContentPane().add(bottomPanel);

		table.getModel().addTableModelListener(this);

		pack();
		setVisible(true);
	}

	public Progress getProgress(String title) {
		return new ProgressDialog(this, title);
	}

	public void valueChanged(ListSelectionEvent event) {
		table.requestFocusInWindow();
		pluginsChanged();
	}

	List<PluginAction> pluginActions = new ArrayList<PluginAction>();

	class PluginAction extends JButton implements ActionListener {
		String label, otherLabel;
		Action action, otherAction;

		PluginAction(String label, Action action) {
			this(label, action, null, null);
		}

		PluginAction(String label, Action action,
				String otherLabel, Action otherAction) {
			super(label);
			this.label = label;
			this.action = action;
			this.otherLabel = otherLabel;
			this.otherAction = otherAction;
			addActionListener(this);
			pluginActions.add(this);
		}

		public void actionPerformed(ActionEvent e) {
			if (table.isEditing())
				table.editingCanceled(null);
			for (PluginObject plugin : table.getSelectedPlugins()) {
				if (action == null)
					plugin.setNoAction();
				else {
					Status status = plugin.getStatus();
					if (status.isValid(action))
						plugin.setAction(action);
					else if (status.isValid(otherAction))
						plugin.setAction(otherAction);
					else
						continue;
				}
				table.firePluginChanged(plugin);
			}
		}

		public void enableIfValid() {
			boolean enable = false, enableOther = false;

			for (PluginObject plugin : table.getSelectedPlugins()) {
				Status status = plugin.getStatus();
				if (action == null)
					enable = true;
				else if (status.isValid(action))
					enable = true;
				else if (status.isValid(otherAction))
					enableOther = true;
				if (enable && enableOther)
					break;
			}
			setLabel(!enableOther ? label :
				(enable ? label + "/" : "") + otherLabel);
			setEnabled(enable || enableOther);
		}
	}

	public void updatePluginsTable() {
		Iterable<PluginObject> view = viewOptions.getView(table);
		// TODO: maybe we want to remember what was selected?
		table.clearSelection();

		String search = txtSearch.getText().trim();
		if (!search.equals(""))
			view = PluginCollection.filter(search, view);

		//Directly update the table for display
		table.setPlugins(view);
	}

	// TODO: once the editor is embedded, this can go
	private PluginObject getSingleSelectedPlugin() {
		int[] rows = table.getSelectedRows();
		return rows.length != 1 ? null : table.getPlugin(rows[0]);
	}

	// TODO: why should this function need to know that it is triggered by a click?  That is so totally unnecessary.
	private void clickToEditDescriptions() {
		// TODO: embed this, rather than having an extra editor
		PluginObject plugin = getSingleSelectedPlugin();
		if (plugin == null) {
			IJ.error("Cannot edit multiple items at once");
			return;
		}
		loadedFrame = new DetailsEditor(this, plugin);
		showFrame();
		setEnabled(false);
	}

	private void clickToBeginOperations() {
		// TODO: check conflicts
		new Thread() {
			public void run() {
				download();
			}
		}.start();
	}

	private void quit() {
		if (plugins.hasChanges() &&
				JOptionPane.showConfirmDialog(this,
					"You have specified changes. Are you "
					+ "sure you want to quit?",
					"Quit?", JOptionPane.YES_NO_OPTION,
					JOptionPane.WARNING_MESSAGE) !=
				JOptionPane.YES_OPTION)
			return;
		dispose();
	}

	private void showFrame() {
		if (loadedFrame != null) {
			loadedFrame.setVisible(true);
			loadedFrame.setLocationRelativeTo(null); //center of the screen
		}
	}

	public void download() {
		Installer installer =
			new Installer(getProgress("Installing..."));
		try {
			installer.start();
			updatePluginsTable();
		} catch (Canceled e) {
			// TODO: remove "update/" directory
			IJ.error("Canceled");
			installer.done();
		} catch (IOException e) {
			// TODO: remove "update/" directory
			// TODO: make error() method
			IJ.error("Installer failed: " + e);
			installer.done();
		}
	}

	public void exitWithRestartFijiMessage() {
		removeLoadedFrameIfExists();
		IJ.showMessage("Restart Fiji", "You must restart Fiji application for the Plugin status changes to take effect.");
		dispose();
	}

	public void exitWithRestartMessage(String title, String message) {
		IJ.showMessage(title, message);
		dispose();
	}

	private void removeLoadedFrameIfExists() {
		if (loadedFrame != null) {
			loadedFrame.setVisible(false);
			loadedFrame.dispose();
			loadedFrame = null;
		}
	}

	public void pluginsChanged() {
		// TODO: once this is editable, make sure changes are committed
		pluginDetails.setText("");
		for (PluginObject plugin : table.getSelectedPlugins())
			pluginDetails.showPluginDetails(plugin);

		for (PluginAction button : pluginActions)
			button.enableIfValid();

		btnStart.setEnabled(plugins.hasChanges());

		// TODO: "Upload" is activated by default!"
		if (Util.isDeveloper) {
			btnEditDetails.setEnabled(getSingleSelectedPlugin()
					!= null);
			btnUpload.setEnabled(plugins.hasUpload());
		}
	}

	public void tableChanged(TableModelEvent e) {
		int size = plugins.size();
		int install = 0;
		int remove = 0;
		int update = 0;
		int upload = 0;

		//Refresh count information
		for (PluginObject myPlugin : plugins)
			if (myPlugin.toInstall())
				install += 1;
			else if (myPlugin.toRemove())
				remove += 1;
			else if (myPlugin.toUpdate())
				update += 1;
			else if (myPlugin.toUpload())
				upload += 1;
		String text = "Total: " + size + ", To install: " + install
			+ ", To remove: " + remove + ", To update: " + update;
		if (Util.isDeveloper)
			text += ", To upload: " + upload;
		lblPluginSummary.setText(text);

		pluginsChanged();
	}

	private void enableIfAnyUpload(JButton button) {
		enableIfActions(button, plugins.hasUpload());
	}

	private void enableIfAnyChange(JButton button) {
		enableIfActions(button, plugins.hasChanges());
	}

	private void enableIfActions(JButton button, boolean flag) {
		button.setEnabled(flag);
	}

	public void setLastModified(long lastModified) {
		xmlLastModified = lastModified;

		String list = null;
		for (PluginObject plugin : plugins) {
			File file = new File(Util.prefix(plugin.getFilename()));
			if (!file.exists() || file.canWrite())
				continue;
			if (list == null)
				list = plugin.getFilename();
			else
				list += ", " + plugin.getFilename();
		}
		if (list != null)
			IJ.showMessage("Read-only Plugins",
					"WARNING: The following plugin files "
					+ "are set to read-only: '"
					+ list + "'");
	}

	protected void upload() {
		PluginUploader uploader = new PluginUploader(xmlLastModified);

		try {
			if (!interactiveSshLogin(uploader))
				return;
			uploader.upload(getProgress("Uploading..."));
			// TODO: download list instead
			IJ.showMessage("You need to restart this plugin now");
		} catch (Canceled e) {
			// TODO: teach uploader to remove the lock file
			IJ.error("Canceled");
		} catch (Throwable e) {
			e.printStackTrace();
			IJ.error("Upload failed: " + e);
		}
	}

	protected boolean interactiveSshLogin(PluginUploader uploader) {
		String username = Prefs.get(Updater.PREFS_USER, "");
		String password = "";
		do {
			//Dialog to enter username and password
			GenericDialog gd = new GenericDialog("Login");
			gd.addStringField("Username", username, 20);
			gd.addStringField("Password", "", 20);

			final TextField user =
				(TextField)gd.getStringFields().firstElement();
			final TextField pwd =
				(TextField)gd.getStringFields().lastElement();
			pwd.setEchoChar('*');
			if (!username.equals(""))
				user.addFocusListener(new FocusAdapter() {
					public void focusGained(FocusEvent e) {
						pwd.requestFocus();
						user.removeFocusListener(this);
					}
				});

			gd.showDialog();
			if (gd.wasCanceled())
				return false; //return back to user interface

			//Get the required login information
			username = gd.getNextString();
			password = gd.getNextString();

		} while (!uploader.setLogin(username, password));

		Prefs.set(Updater.PREFS_USER, username);
		return true;
	}

}