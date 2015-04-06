package edu.virginia.vcgr.genii.ui.plugins.files;

import java.awt.Component;
import java.awt.Container;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JRadioButton;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.ws.addressing.EndpointReferenceType;

import edu.virginia.vcgr.genii.client.gui.GuiHelpAction;
import edu.virginia.vcgr.genii.client.gui.GuiUtils;
import edu.virginia.vcgr.genii.client.gui.HelpLinkConfiguration;
import edu.virginia.vcgr.genii.client.gui.exportdir.IInformationListener;
import edu.virginia.vcgr.genii.client.gui.exportdir.ResourcePathsWidget;
import edu.virginia.vcgr.genii.client.rcreate.ResourceCreationContext;
import edu.virginia.vcgr.genii.client.rcreate.ResourceCreator;
import edu.virginia.vcgr.genii.client.resource.TypeInformation;
import edu.virginia.vcgr.genii.client.rns.RNSPath;
import edu.virginia.vcgr.genii.client.rns.RNSPathAlreadyExistsException;
import edu.virginia.vcgr.genii.client.rns.RNSPathDoesNotExistException;
import edu.virginia.vcgr.genii.client.rns.RNSPathQueryFlags;
import edu.virginia.vcgr.genii.client.utils.flock.FileLockException;
import edu.virginia.vcgr.genii.ui.plugins.UIPluginContext;
import edu.virginia.vcgr.genii.ui.utils.LoggingTarget;

public class CreateDirDialog extends JDialog
{
	static final long serialVersionUID = 0L;
	static private Log _logger = LogFactory.getLog(CreateDirDialog.class);

	static final private String _TITLE = "Create Directory Tool";
	private ResourcePathsWidget _paths;

	String _container_path;
	String _target_path;
	UIPluginContext _context;
	private JRadioButton _keepWithParent = null;
	private JRadioButton _selectNewContainer = null;

	private class QuitButton extends AbstractAction
	{
		static final long serialVersionUID = 0L;

		public QuitButton()
		{
			super("Cancel");
		}

		@Override
		public void actionPerformed(ActionEvent e)
		{
			setVisible(false);
		}
	}

	public CreateDirDialog(UIPluginContext context, String ContainerPath, String TargetPath) throws FileLockException
	{
		Container container;
		_container_path = ContainerPath;
		_target_path = TargetPath;
		_context = context;

		setTitle(_TITLE);

		container = getContentPane();
		container.setLayout(new GridBagLayout());

		container.add(_keepWithParent = new JRadioButton("Recommended: Store with parent directory"), new GridBagConstraints(0, 0, 2, 1, 1.0,
			0.0, GridBagConstraints.WEST, GridBagConstraints.NONE, new Insets(5, 5, 5, 5), 0, 0));
		container.add(_selectNewContainer = new JRadioButton("Choose a new container - for control over placement"), new GridBagConstraints(
			0, 1, 2, 1, 1.0, 0.0, GridBagConstraints.WEST, GridBagConstraints.NONE, new Insets(5, 5, 5, 5), 5, 5));

		_keepWithParent.setSelected(true);
		_selectNewContainer.setSelected(false);
		// _selectNewContainer.isSelected()
		container.add(_paths = new ResourcePathsWidget(false, true, _container_path, _target_path), new GridBagConstraints(0, 2, 2, 1, 1.0,
			1.0, GridBagConstraints.WEST, GridBagConstraints.BOTH, new Insets(5, 5, 5, 5), 5, 5));
		_paths.disableContainerPath();

		_keepWithParent.addActionListener(new ActionListener()
		{
			public void actionPerformed(ActionEvent e)
			{
				_paths.disableContainerPath();
				// Execute when button is pressed
				// System.out.println("You clicked the button");
			}
		});
		_selectNewContainer.addActionListener(new ActionListener()
		{
			public void actionPerformed(ActionEvent e)
			{
				_paths.enableContainerPath();
				// Execute when button is pressed
				// System.out.println("You clicked the button");
			}
		});

		ButtonGroup group = new ButtonGroup();
		group.add(_keepWithParent);
		group.add(_selectNewContainer);

		CreateDirAction action = new CreateDirAction();
		_paths.addInformationListener(action);
		/*
		 * container.add(new JButton(new QuitButton()), new GridBagConstraints(0, 3, 1, 1, 0.0, 1.0, GridBagConstraints.EAST,
		 * GridBagConstraints.NONE, new Insets(5, 5, 5, 5), 5, 5));
		 * 
		 * container.add(new JButton(new GuiHelpAction(null,
		 * HelpLinkConfiguration.get_help_url(HelpLinkConfiguration.CREATE_DIRECTORY_HELP))), new GridBagConstraints(1, 3, 1, 1, 1.0, 0.0,
		 * GridBagConstraints.EAST, GridBagConstraints.NONE, new Insets(5, 5, 5, 5), 5, 5));
		 */

		container.add(createButtonPanel(action), new GridBagConstraints(0, 3, 2, 1, 1.0, 0.0, GridBagConstraints.CENTER,
			GridBagConstraints.HORIZONTAL, new Insets(5, 5, 5, 5), 5, 5));
	}

	private Component createButtonPanel(Action action)
	{
		JPanel panel = new JPanel(new GridBagLayout());

		panel.add(new JButton(action), new GridBagConstraints(0, 0, 1, 1, 0.0, 1.0, GridBagConstraints.CENTER, GridBagConstraints.NONE,
			new Insets(5, 5, 5, 5), 5, 5));
		panel.add(new JButton(new QuitButton()), new GridBagConstraints(1, 0, 1, 1, 0.0, 1.0, GridBagConstraints.CENTER,
			GridBagConstraints.NONE, new Insets(5, 5, 5, 5), 5, 5));
		panel.add(new JButton(new GuiHelpAction(null, HelpLinkConfiguration.get_help_url(HelpLinkConfiguration.CREATE_DIRECTORY_HELP))),
			new GridBagConstraints(2, 0, 1, 1, 1.0, 1.0, GridBagConstraints.EAST, GridBagConstraints.NONE, new Insets(5, 5, 5, 5), 5, 5));

		return panel;
	}

	private boolean hasEnoughInformationToCreate()
	{

		String rnsPath = _paths.getRNSPath();
		String containerPath = _paths.getContainerPath();
		RNSPath path = RNSPath.getCurrent();

		if (_selectNewContainer.isSelected()) {
			if (containerPath.compareTo("DEFAULT") == 0) {
				_paths.setContainerPath("/");
				return false;
			}
			try {
				path = path.lookup(containerPath, RNSPathQueryFlags.MUST_EXIST);
				path = path.lookup(containerPath + "/Services/EnhancedRNSPortType", RNSPathQueryFlags.MUST_EXIST);
			} catch (RNSPathDoesNotExistException r) {
				// JOptionPane.showMessageDialog(null, containerPath,
				// "Storage container path you selected does not exist or you do not have permission",
				// JOptionPane.ERROR_MESSAGE);
				// lets keep what they already typed and let them edit it instead
				// containerPath = null;
				return false;
			} catch (RNSPathAlreadyExistsException er) {
			}

		}
		// ASG 2014-06-02 ASG changed logic so that we don't care about container path if
		// selectNewContainr is false
		return rnsPath != null
			&& rnsPath.trim().length() > 0
			&& ((_selectNewContainer.isSelected() && containerPath != null && containerPath.trim().length() > 0) || !_selectNewContainer
				.isSelected());
	}

	private class CreateDirAction extends AbstractAction implements IInformationListener
	{
		static final long serialVersionUID = 0L;

		public CreateDirAction()
		{
			super("Create Directory");

			setEnabled(hasEnoughInformationToCreate());
		}

		@Override
		public void actionPerformed(ActionEvent e)
		{
			String rnsPath = _paths.getRNSPath();
			String containerPath = _paths.getContainerPath();

			// Do the create

			try {

				RNSPath path = RNSPath.getCurrent();
				RNSPath linkPath = path;
				// First check the target RNS path
				if ((_target_path.compareTo(rnsPath) != 0) && ((_target_path + "/").compareTo(rnsPath) != 0)) {
					try {
						path = path.lookup(rnsPath, RNSPathQueryFlags.MUST_NOT_EXIST);
						linkPath = path;
					} catch (RNSPathAlreadyExistsException r) {
						String msg = "Indicated path " + rnsPath + " already exists ";
						JOptionPane.showMessageDialog(null, rnsPath, "Indicated path already exists", JOptionPane.ERROR_MESSAGE);
						rnsPath = null;
						LoggingTarget.logInfo(msg, r);
						return;
					} catch (RNSPathDoesNotExistException er) {
						_logger.error("caught unexpected exception", er);
						return;
					}
				}
				// Why was there no else here?
				else {
					String msg = "Indicated path " + rnsPath + " already exists, must select a path that does not exist ";
					JOptionPane.showMessageDialog(null, rnsPath, "Indicated path already exists", JOptionPane.ERROR_MESSAGE);
					rnsPath = null;
					LoggingTarget.logInfo(msg, null);
					return;
				}
				if (!path.getParent().exists()) {
					String msg = "Parent directory " + path.getParent().toString() + " does not exist";
					JOptionPane.showMessageDialog(null, rnsPath, "Parent directory " + path.getParent().toString() + " does not exist!",
						JOptionPane.ERROR_MESSAGE);
					LoggingTarget.logInfo(msg, null);
					rnsPath = null;
					return;
				}

				if (_selectNewContainer.isSelected()) {
					try {
						path = path.lookup(containerPath, RNSPathQueryFlags.MUST_EXIST);
						path = path.lookup(containerPath + "/Services/EnhancedRNSPortType", RNSPathQueryFlags.MUST_EXIST);
					} catch (RNSPathDoesNotExistException r) {
						String msg = "Container " + path.getParent().toString() + " does not exist or you do not have permission";
						JOptionPane.showMessageDialog(null, rnsPath, "Container" + path.getParent().toString()
							+ " does not exist or you have no permission!", JOptionPane.ERROR_MESSAGE);
						LoggingTarget.logInfo(msg, null);
						return;
					} catch (RNSPathAlreadyExistsException er) {
					}

					if (new TypeInformation(path.lookup(containerPath).getEndpoint()).isContainer()) {
						// System.err.println("About to mkdir: container " + path.toString() +
						// " : link to " + linkPath.toString());
						EndpointReferenceType newEPR =
							ResourceCreator.createNewResource(path.getEndpoint(), null, new ResourceCreationContext());
						linkPath.link(newEPR);

					} else {
						JOptionPane.showMessageDialog(null, containerPath, "Storage container is not a container", JOptionPane.ERROR_MESSAGE);
						LoggingTarget.logInfo("The path " + containerPath + " is not a storage container!", null);
						containerPath = null;
					}
				} else {
					path.mkdir();
				}

				setVisible(false);
				_context.endpointRetriever().refresh();
			} catch (Throwable cause) {
				GuiUtils.displayError((Component) e.getSource(), "Directory Creation Error", cause);
			}

		}

		@Override
		public void updateInformation()
		{
			setEnabled(hasEnoughInformationToCreate());
		}
	}

}
