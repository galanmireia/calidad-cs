package com.ibm.nmon.data.transform;

import java.util.List;

import com.ibm.nmon.data.DataRecord;
import com.ibm.nmon.data.DataSet;
import com.ibm.nmon.data.DataType;
import com.ibm.nmon.data.Process;
import com.ibm.nmon.data.ProcessDataSet;
import com.ibm.nmon.data.SubDataType;

/**
 * Post processor to scale Windows <code>Process</code> CPU utilization data by the currently active
 * number of CPUs.
 */
public class WindowsProcessPostProcessor implements DataPostProcessor {
    private final List<DataType> processors = new java.util.ArrayList<DataType>(8);

    @Override
    public void addDataTypes(DataSet data) {
        // get all CPUs
        processors.clear();

        for (DataType type : data.getTypes()) {
            if ((type instanceof SubDataType)) {
                SubDataType subType = ((SubDataType) type);

                if (subType.getPrimaryId().equals("Processor") && !subType.getSubId().equals("Total")) {
                    processors.add(type);
                }
            }
        }
    }

    @Override
    public void postProcess(DataSet data, DataRecord record) {
        if (data instanceof ProcessDataSet) {
            ProcessDataSet processData = (ProcessDataSet) data;
            // for each record, count the number of active CPUs
            int processorCount = 0;

            if (processors.isEmpty()) {
                // no data => do not scale
                return;
            }
            else {
                for (DataType processorType : processors) {
                    if (record.hasData(processorType)) {
                        ++processorCount;
                    }
                }
            }

            // scale all the process data by the number of CPUs
            for (Process process : processData.getProcesses()) {
                DataType processType = processData.getType(process);

                if (record.hasData(processType)) {
                    for (String field : processType.getFields()) {
                        if (field.startsWith("%")) {
                            // assume % Processor Time, % User Time or % Privileged Time
                            record.getData(processType)[processType.getFieldIndex(field)] /= processorCount;
                        }
                    }
                }
            }
        }
    }
}
