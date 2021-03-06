package edu.virginia.vcgr.genii.gjt.data.variables.doubleloop;

import java.awt.Window;

import edu.virginia.vcgr.genii.gjt.data.variables.VariableDefinition;
import edu.virginia.vcgr.genii.gjt.data.variables.VariableDefinitionEditor;
import edu.virginia.vcgr.genii.gjt.data.variables.VariableDefinitionEditorFactory;

public class DoubleLoopVariableDefinitionEditorFactory implements VariableDefinitionEditorFactory
{
	@Override
	public VariableDefinitionEditor<? extends VariableDefinition> createEditor(Window owner)
	{
		return new DoubleLoopVariableDefinitionEditor(owner);
	}
}