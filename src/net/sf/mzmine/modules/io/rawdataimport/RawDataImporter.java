/*
 * Copyright 2006-2008 The MZmine Development Team
 * 
 * This file is part of MZmine.
 * 
 * MZmine is free software; you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 2 of the License, or (at your option) any later
 * version.
 * 
 * MZmine is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
 * A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License along with
 * MZmine; if not, write to the Free Software Foundation, Inc., 51 Franklin St,
 * Fifth Floor, Boston, MA 02110-1301 USA
 */

package net.sf.mzmine.modules.io.rawdataimport;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.io.File;
import java.io.IOException;
import java.util.logging.Logger;

import net.sf.mzmine.data.ParameterSet;
import net.sf.mzmine.data.PeakList;
import net.sf.mzmine.data.PreloadLevel;
import net.sf.mzmine.data.RawDataFile;
import net.sf.mzmine.data.RawDataFileWriter;
import net.sf.mzmine.desktop.Desktop;
import net.sf.mzmine.desktop.MZmineMenu;
import net.sf.mzmine.main.MZmineCore;
import net.sf.mzmine.main.MZmineModule;
import net.sf.mzmine.modules.batchmode.BatchStep;
import net.sf.mzmine.modules.batchmode.BatchStepCategory;
import net.sf.mzmine.modules.io.rawdataimport.fileformats.MzDataReadTask;
import net.sf.mzmine.modules.io.rawdataimport.fileformats.MzXMLReadTask;
import net.sf.mzmine.modules.io.rawdataimport.fileformats.NetCDFReadTask;
import net.sf.mzmine.taskcontrol.Task;
import net.sf.mzmine.taskcontrol.TaskGroup;
import net.sf.mzmine.taskcontrol.TaskGroupListener;
import net.sf.mzmine.taskcontrol.TaskListener;
import net.sf.mzmine.util.dialogs.ExitCode;

/**
 * work in progress...
 */
public class RawDataImporter implements MZmineModule, ActionListener, TaskListener, BatchStep {

    private Logger logger = Logger.getLogger(this.getClass().getName());
    
    private RawDataImporterParameters parameters;

    private Desktop desktop;


    /**
     * @see net.sf.mzmine.main.MZmineModule#initModule(net.sf.mzmine.main.MZmineCore)
     */
    public void initModule() {

        this.desktop = MZmineCore.getDesktop();

        parameters = new RawDataImporterParameters();

        desktop.addMenuItem(MZmineMenu.PROJECT, "Import raw data files DEVEL",
                "This module imports raw data files of various formats",
                KeyEvent.VK_W, this, null);
    }

    /**
     * @see net.sf.mzmine.main.MZmineModule#getParameterSet()
     */
    public ParameterSet getParameterSet() {
        return parameters;
    }

    /**
     * @see net.sf.mzmine.main.MZmineModule#setParameters(net.sf.mzmine.data.ParameterSet)
     */
    public void setParameters(ParameterSet parameters) {
        this.parameters = (RawDataImporterParameters) parameters;
    }

    public void actionPerformed(ActionEvent event) {

        ExitCode setupExitCode = setupParameters(parameters);

        if (setupExitCode != ExitCode.OK) {
            return;
        }

        runModule(null, null, parameters, null);
        
    }

    public BatchStepCategory getBatchStepCategory() {
        return BatchStepCategory.PROJECT;
    }

    public TaskGroup runModule(RawDataFile[] dataFiles, PeakList[] peakLists,
            ParameterSet parameters, TaskGroupListener taskGroupListener) {

        // TODO
		    RawDataImporterParameters rawDataImporterParameters = (RawDataImporterParameters) parameters;
		    File file[]=rawDataImporterParameters.getFileNames();
	        Task openTasks[] = new Task[file.length];

	        for (int i = 0; i < file.length; i++) {
	    	
                String extension = file[i].getName().substring(file[i].getName().lastIndexOf(".") + 1).toLowerCase();
             
                if (extension.endsWith("mzdata")) {	    	
	                openTasks[i] = new MzDataReadTask(file[i],
	            		(PreloadLevel) rawDataImporterParameters.getParameterValue(RawDataImporterParameters.preloadLevel));
                }
                if(extension.endsWith("mzxml")) {	    	
	                openTasks[i] = new MzXMLReadTask(file[i],
	            		(PreloadLevel) rawDataImporterParameters.getParameterValue(RawDataImporterParameters.preloadLevel));
                }
                if (extension.endsWith("cdf")) {	    	
	                openTasks[i] = new NetCDFReadTask(file[i],
	            		(PreloadLevel) rawDataImporterParameters.getParameterValue(RawDataImporterParameters.preloadLevel));
                }
                if(!extension.endsWith("mzxml") && !extension.endsWith("mzdata") && !extension.endsWith("cdf")) {	    	
                    desktop.displayErrorMessage("Cannot determine file type of file "+ file[i]);                    
                	logger.finest("Cannot determine file type of file "+ file[i]);
                    return null;
                }
	        }
 		TaskGroup newGroup = new TaskGroup(openTasks, this, taskGroupListener);
        // start this group
        newGroup.start();
        return newGroup;
    }



    public ExitCode setupParameters(ParameterSet parameters) {

        FileOpenDialog dialog = new FileOpenDialog(
                (RawDataImporterParameters) parameters);

        dialog.setVisible(true);

        return dialog.getExitCode();
    }

    public void taskStarted(Task task) {
        Task openTask = task;
         logger.info("Started action of " + openTask.getTaskDescription());
    }    
    
    public void taskFinished(Task task) {

 		if (task instanceof MzDataReadTask) {
            if (task.getStatus() == Task.TaskStatus.FINISHED) {
            	logger.info("Finished action of " + task.getTaskDescription());
            }
 		}

 		if (task instanceof MzXMLReadTask) {
            if (task.getStatus() == Task.TaskStatus.FINISHED) {
            	logger.info("Finished action of " + task.getTaskDescription());
            }
 		}

 		if (task instanceof NetCDFReadTask) {
            if (task.getStatus() == Task.TaskStatus.FINISHED) {
            	logger.info("Finished action of " + task.getTaskDescription());
            }
 		}
 		
        if (task.getStatus() == Task.TaskStatus.ERROR) {
            String msg = "Error while trying to "
                    + task.getTaskDescription() + ": " + task.getErrorMessage();
            logger.severe(msg);
            desktop.displayErrorMessage(msg);
        }

    }
    
	public RawDataFileWriter createNewFile(String fileName, String suffix,
			PreloadLevel preloadLevel) throws IOException {
		return new RawDataFileImpl(fileName, suffix, preloadLevel);
	}

	/**
	 */
	public RawDataFileWriter createNewFile(File file, PreloadLevel preloadLevel)
			throws IOException {
		return new RawDataFileImpl(file.getName(), "scan", preloadLevel);
	}    

}
