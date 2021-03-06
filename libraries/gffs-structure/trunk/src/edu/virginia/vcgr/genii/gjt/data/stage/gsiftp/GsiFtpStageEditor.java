package edu.virginia.vcgr.genii.gjt.data.stage.gsiftp;

import java.awt.Container;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Window;

import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;

import edu.virginia.vcgr.genii.gjt.data.stage.StageEditor;
import edu.virginia.vcgr.genii.gjt.gui.util.ButtonPanel;

class GsiFtpStageEditor extends StageEditor<GsiFtpStageData>
{
	static final long serialVersionUID = 0L;

	private JTextField _hostname = new JTextField(32);
	private JSpinner _port = new JSpinner(new SpinnerNumberModel(GsiFtpStageData.DEFAULT_GSIFTP_PORT, 1, Integer.MAX_VALUE, 1));
	private JTextField _path = new JTextField(32);

	// private JTextField _username = new JTextField(16);
	// private JPasswordField _password = new JPasswordField(16);

	@Override
	protected GsiFtpStageData getStageDataImpl()
	{
		GsiFtpStageData ret = new GsiFtpStageData();

		ret.host(_hostname.getText());
		ret.path(_path.getText());
		ret.port(((Integer) _port.getValue()).intValue());
		return ret;
	}

	GsiFtpStageEditor(Window owner)
	{
		super(owner, "FTP Stage Editor");

		Container container = getContentPane();
		container.setLayout(new GridBagLayout());

		container.add(new JLabel("Hostname"),
			new GridBagConstraints(0, 0, 1, 1, 0.0, 0.0, GridBagConstraints.WEST, GridBagConstraints.NONE, new Insets(5, 5, 5, 5), 5, 5));
		container.add(_hostname, new GridBagConstraints(1, 0, 1, 1, 1.0, 0.0, GridBagConstraints.CENTER, GridBagConstraints.HORIZONTAL,
			new Insets(5, 5, 5, 5), 5, 5));
		container.add(new JLabel("Port"),
			new GridBagConstraints(2, 0, 1, 1, 0.0, 0.0, GridBagConstraints.WEST, GridBagConstraints.NONE, new Insets(5, 5, 5, 5), 5, 5));
		container.add(_port, new GridBagConstraints(3, 0, 1, 1, 0.0, 0.0, GridBagConstraints.CENTER, GridBagConstraints.HORIZONTAL,
			new Insets(5, 5, 5, 5), 5, 5));

		container.add(new JLabel("Path"),
			new GridBagConstraints(0, 1, 1, 1, 0.0, 0.0, GridBagConstraints.WEST, GridBagConstraints.NONE, new Insets(5, 5, 5, 5), 5, 5));
		container.add(_path, new GridBagConstraints(1, 1, 3, 1, 1.0, 0.0, GridBagConstraints.CENTER, GridBagConstraints.HORIZONTAL,
			new Insets(5, 5, 5, 5), 5, 5));

		JButton okButton = new JButton(createDefaultOKAction());
		getRootPane().setDefaultButton(okButton);
		container.add(ButtonPanel.createHorizontalPanel(okButton, createDefaultCancelAction()), new GridBagConstraints(0, 3, 4, 1, 1.0, 1.0,
			GridBagConstraints.SOUTH, GridBagConstraints.HORIZONTAL, new Insets(5, 5, 5, 5), 5, 5));
	}

	@Override
	public void setInitialData(GsiFtpStageData stageData)
	{
		_hostname.setText(stageData.host());
		_port.setValue(new Integer(stageData.port()));
		_path.setText(stageData.path());
	}
}