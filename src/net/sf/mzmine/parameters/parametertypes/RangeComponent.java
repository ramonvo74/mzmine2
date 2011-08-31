/*
 * Copyright 2006-2011 The MZmine 2 Development Team
 * 
 * This file is part of MZmine 2.
 * 
 * MZmine 2 is free software; you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 2 of the License, or (at your option) any later
 * version.
 * 
 * MZmine 2 is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
 * A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License along with
 * MZmine 2; if not, write to the Free Software Foundation, Inc., 51 Franklin St,
 * Fifth Floor, Boston, MA 02110-1301 USA
 */

package net.sf.mzmine.parameters.parametertypes;

import java.awt.GridBagConstraints;
import java.text.NumberFormat;

import javax.swing.JLabel;
import javax.swing.JTextField;

import net.sf.mzmine.util.Range;
import net.sf.mzmine.util.components.GridBagPanel;

/**
 */
public class RangeComponent extends GridBagPanel {

	private JTextField minTxtField, maxTxtField;
	private NumberFormat format;

	public RangeComponent(NumberFormat format) {

		this.format = format;
		
		minTxtField = new JTextField();
		minTxtField.setColumns(8);
		
		maxTxtField = new JTextField();
		maxTxtField.setColumns(8);
		
		add(minTxtField, 0, 0, 1, 1, 1, 0, GridBagConstraints.HORIZONTAL);
		add(new JLabel(" - "), 1, 0, 1, 1, 0, 0);
		add(maxTxtField, 2, 0, 1, 1, 1, 0, GridBagConstraints.HORIZONTAL);
	}

	public Range getValue() {
		String minString = minTxtField.getText();
		String maxString = maxTxtField.getText();
		
		try {
			Number minValue = format.parse(minString);
			Number maxValue = format.parse(maxString);
			
			if ((minValue == null) || (maxValue == null))
				return null;
			return new Range(minValue.doubleValue(), maxValue.doubleValue());

		}
		catch (Exception e) {
			return null;
		}
	}
	
	public void setNumberFormat(NumberFormat format) {
		this.format = format;
	}

	public void setValue(Range value) {
		minTxtField.setText(format.format(value.getMin()));
		maxTxtField.setText(format.format(value.getMax()));
	}
	
	@Override
	public void setToolTipText(String toolTip) {
		minTxtField.setToolTipText(toolTip);
		minTxtField.setToolTipText(toolTip);
	}

}
