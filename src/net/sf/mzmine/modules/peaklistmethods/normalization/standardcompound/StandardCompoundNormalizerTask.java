/*
 * Copyright 2006-2010 The MZmine 2 Development Team
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
 * MZmine 2; if not, write to the Free Software Foundation, Inc., 51 Franklin
 * St, Fifth Floor, Boston, MA 02110-1301 USA
 */

package net.sf.mzmine.modules.peaklistmethods.normalization.standardcompound;

import java.util.logging.Logger;

import net.sf.mzmine.data.ChromatographicPeak;
import net.sf.mzmine.data.PeakList;
import net.sf.mzmine.data.PeakListAppliedMethod;
import net.sf.mzmine.data.PeakListRow;
import net.sf.mzmine.data.RawDataFile;
import net.sf.mzmine.data.impl.SimpleChromatographicPeak;
import net.sf.mzmine.data.impl.SimplePeakList;
import net.sf.mzmine.data.impl.SimplePeakListAppliedMethod;
import net.sf.mzmine.data.impl.SimplePeakListRow;
import net.sf.mzmine.main.MZmineCore;
import net.sf.mzmine.modules.peaklistmethods.normalization.linear.LinearNormalizerParameters;
import net.sf.mzmine.project.MZmineProject;
import net.sf.mzmine.taskcontrol.Task;
import net.sf.mzmine.taskcontrol.TaskStatus;
import net.sf.mzmine.util.CollectionUtils;
import net.sf.mzmine.util.PeakUtils;

public class StandardCompoundNormalizerTask implements Task {

    private Logger logger = Logger.getLogger(this.getClass().getName());

    private PeakList originalPeakList, normalizedPeakList;

    private TaskStatus taskStatus = TaskStatus.WAITING;
    private String errorMessage;

    private int processedRows, totalRows;

    private String suffix, normalizationType, peakMeasurementType;
    private double MZvsRTBalance;
    private boolean removeOriginal;
    private PeakListRow[] standardRows;
    private StandardCompoundNormalizerParameters parameters;

    public StandardCompoundNormalizerTask(PeakList peakList,
            StandardCompoundNormalizerParameters parameters) {

        this.originalPeakList = peakList;

        suffix = (String) parameters.getParameterValue(LinearNormalizerParameters.suffix);
        normalizationType = (String) parameters.getParameterValue(StandardCompoundNormalizerParameters.standardUsageType);
        peakMeasurementType = (String) parameters.getParameterValue(StandardCompoundNormalizerParameters.peakMeasurementType);
        MZvsRTBalance = (Double) parameters.getParameterValue(StandardCompoundNormalizerParameters.MZvsRTBalance);
        removeOriginal = (Boolean) parameters.getParameterValue(StandardCompoundNormalizerParameters.autoRemove);
        Object stdRowObjects[] = (Object[]) parameters.getParameterValue(StandardCompoundNormalizerParameters.standardCompounds);
        standardRows = CollectionUtils.changeArrayType(stdRowObjects, PeakListRow.class);
        
    }

    public void cancel() {
        taskStatus = TaskStatus.CANCELED;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public double getFinishedPercentage() {
        if (totalRows == 0)
            return 0;
        return (double) processedRows / (double) totalRows;
    }

    public TaskStatus getStatus() {
        return taskStatus;
    }

    public String getTaskDescription() {
        return "Standard compound normalization of " + originalPeakList;
    }

    public void run() {

        taskStatus = TaskStatus.PROCESSING;

        logger.finest("Starting standard compound normalization of "
                + originalPeakList
                + " using "
                + normalizationType
                + " (total "
                + standardRows.length + " standard peaks)");

        // Initialize new alignment result for the normalized result
		normalizedPeakList = new SimplePeakList(
				originalPeakList
                + " " + suffix, originalPeakList.getRawDataFiles());

        // Copy raw data files from original alignment result to new alignment
        // result
        totalRows = originalPeakList.getNumberOfRows();

        // Loop through all rows
        rowIteration: for (PeakListRow row : originalPeakList.getRows()) {

            // Cancel ?
            if (taskStatus == TaskStatus.CANCELED) {
                return;
            }

            // Do not add the standard rows to the new peaklist
            for (int i = 0; i < standardRows.length; i++) {
                if (row == standardRows[i]) {
                    processedRows++;
                    continue rowIteration;
                }
            }

            // Copy comment and identification
            SimplePeakListRow normalizedRow = new SimplePeakListRow(row.getID());
            PeakUtils.copyPeakListRowProperties(row, normalizedRow);

            // Get m/z and RT of the current row
            double mz = row.getAverageMZ();
            double rt = row.getAverageRT();

            // Loop through all raw data files
            for (RawDataFile file : originalPeakList.getRawDataFiles()) {

                double normalizationFactors[] = null;
                double normalizationFactorWeights[] = null;

                if (normalizationType == StandardCompoundNormalizerParameters.standardUsageTypeNearest) {

                    // Search for nearest standard
                    PeakListRow nearestStandardRow = null;
                    double nearestStandardRowDistance = Double.MAX_VALUE;

                    for (int standardRowIndex = 0; standardRowIndex < standardRows.length; standardRowIndex++) {
                        PeakListRow standardRow = standardRows[standardRowIndex];

                        double stdMZ = standardRow.getAverageMZ();
                        double stdRT = standardRow.getAverageRT();
                        double distance = MZvsRTBalance * Math.abs(mz - stdMZ)
                                + Math.abs(rt - stdRT);
                        if (distance <= nearestStandardRowDistance) {
                            nearestStandardRow = standardRow;
                            nearestStandardRowDistance = distance;
                        }

                    }

                    // Calc and store a single normalization factor
                    normalizationFactors = new double[1];
                    normalizationFactorWeights = new double[1];
                    ChromatographicPeak standardPeak = nearestStandardRow.getPeak(file);
                    if (peakMeasurementType == StandardCompoundNormalizerParameters.peakMeasurementTypeHeight) {
                        normalizationFactors[0] = standardPeak.getHeight();
                    } else {
                        normalizationFactors[0] = standardPeak.getArea();
                    }
                    logger.finest("Normalizing row #" + row.getID()
                            + " using standard peak " + standardPeak
                            + ", factor " + normalizationFactors[0]);
                    normalizationFactorWeights[0] = 1.0f;

                }

                if (normalizationType == StandardCompoundNormalizerParameters.standardUsageTypeWeighted) {

                    // Add all standards as factors, and use distance as weight
                    normalizationFactors = new double[standardRows.length];
                    normalizationFactorWeights = new double[standardRows.length];

                    for (int standardRowIndex = 0; standardRowIndex < standardRows.length; standardRowIndex++) {
                        PeakListRow standardRow = standardRows[standardRowIndex];

                        double stdMZ = standardRow.getAverageMZ();
                        double stdRT = standardRow.getAverageRT();
                        double distance = MZvsRTBalance * Math.abs(mz - stdMZ)
                                + Math.abs(rt - stdRT);

                        ChromatographicPeak standardPeak = standardRow.getPeak(file);
                        if (standardPeak == null) {
                            // What to do if standard peak is not
                            // available? (Currently this is ruled out by the
                            // setup dialog, which shows only peaks that are
                            // present in all samples)
                            normalizationFactors[standardRowIndex] = 1.0;
                            normalizationFactorWeights[standardRowIndex] = 0.0;
                        } else {
                            if (peakMeasurementType == StandardCompoundNormalizerParameters.peakMeasurementTypeHeight) {
                                normalizationFactors[standardRowIndex] = standardPeak.getHeight();
                            } else {
                                normalizationFactors[standardRowIndex] = standardPeak.getArea();
                            }
                            normalizationFactorWeights[standardRowIndex] = 1 / distance;
                        }
                    }

                }

                // Calculate a single normalization factor as weighted average
                // of all factors
                double weightedSum = 0.0f;
                double sumOfWeights = 0.0f;
                for (int factorIndex = 0; factorIndex < normalizationFactors.length; factorIndex++) {
                    weightedSum += normalizationFactors[factorIndex]
                            * normalizationFactorWeights[factorIndex];
                    sumOfWeights += normalizationFactorWeights[factorIndex];
                }
                double normalizationFactor = weightedSum / sumOfWeights;

                // For simple scaling of the normalized values
                normalizationFactor = normalizationFactor / 100.0f;

                logger.finest("Normalizing row #" + row.getID() + "[" + file
                        + "] using factor " + normalizationFactor);

                // How to handle zero normalization factor?
                if (normalizationFactor == 0.0)
                    normalizationFactor = Double.MIN_VALUE;

                // Normalize peak
                ChromatographicPeak originalPeak = row.getPeak(file);
                if (originalPeak != null) {
                    SimpleChromatographicPeak normalizedPeak = new SimpleChromatographicPeak(
                            originalPeak);
                    double normalizedHeight = originalPeak.getHeight()
                            / normalizationFactor;
                    double normalizedArea = originalPeak.getArea()
                            / normalizationFactor;
                    normalizedPeak.setHeight(normalizedHeight);
                    normalizedPeak.setArea(normalizedArea);

                    normalizedRow.addPeak(file, normalizedPeak);
                }

            }

            normalizedPeakList.addRow(normalizedRow);
            processedRows++;

        }

        // Add new peaklist to the project
        MZmineProject currentProject = MZmineCore.getCurrentProject();
        currentProject.addPeakList(normalizedPeakList);

        // Load previous applied methods
        for (PeakListAppliedMethod proc : originalPeakList.getAppliedMethods()) {
            normalizedPeakList.addDescriptionOfAppliedTask(proc);
        }

        // Add task description to peakList
        normalizedPeakList.addDescriptionOfAppliedTask(new SimplePeakListAppliedMethod(
                "Standard compound normalization", parameters));

        // Remove the original peaklist if requested
        if (removeOriginal)
            currentProject.removePeakList(originalPeakList);

        logger.info("Finished standard compound normalizer");
        taskStatus = TaskStatus.FINISHED;

    }

	public Object[] getCreatedObjects() {
		return new Object[] { normalizedPeakList };
	}

}