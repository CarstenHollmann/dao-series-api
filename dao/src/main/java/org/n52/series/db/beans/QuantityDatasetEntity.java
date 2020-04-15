/*
 * Copyright (C) 2015-2020 52°North Initiative for Geospatial Open Source
 * Software GmbH
 *
 * This program is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 as published
 * by the Free Software Foundation.
 *
 * If the program is linked with libraries which are licensed under one of
 * the following licenses, the combination of the program with the linked
 * library is not considered a "derivative work" of the program:
 *
 *     - Apache License, version 2.0
 *     - Apache Software License, version 1.0
 *     - GNU Lesser General Public License, version 3
 *     - Mozilla Public License, versions 1.0, 1.1 and 2.0
 *     - Common Development and Distribution License (CDDL), version 1.0
 *
 * Therefore the distribution of the program linked with libraries licensed
 * under the aforementioned licenses, is permitted by the copyright holders
 * if the distribution is compliant with both the GNU General Public License
 * version 2 and the aforementioned licenses.
 *
 * This program is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License
 * for more details.
 */
package org.n52.series.db.beans;

import java.util.Date;
import java.util.HashSet;
import java.util.Set;

import org.n52.io.response.dataset.quantity.QuantityDatasetOutput;

public class QuantityDatasetEntity extends DatasetEntity<QuantityDataEntity> {

    private int numberOfDecimals;

    private Set<QuantityDatasetEntity> referenceValues = new HashSet<>();

    public QuantityDatasetEntity() {
        super(QuantityDatasetOutput.VALUE_TYPE);
    }

    public Set<QuantityDatasetEntity> getReferenceValues() {
        return referenceValues;
    }

    public void setReferenceValues(Set<QuantityDatasetEntity> referenceValues) {
        this.referenceValues = referenceValues;
    }

    public int getNumberOfDecimals() {
        return numberOfDecimals;
    }

    public void setNumberOfDecimals(int numberOfDecimals) {
        this.numberOfDecimals = numberOfDecimals;
    }

    @Override
    public QuantityDataEntity getFirstValue() {
        final QuantityDataEntity firstValue = super.getFirstValue();
        if (firstValue != null) {
            Date when = firstValue.getTimeend();
            Double value = firstValue.getValue();
            if (when == null || value == null) {
                // empty component
                return null;
            }
        }
        return firstValue;
    }

    @Override
    public QuantityDataEntity getLastValue() {
        final QuantityDataEntity lastValue = super.getLastValue();
        if (lastValue != null) {
            Date when = lastValue.getTimeend();
            Double value = lastValue.getValue();
            if (when == null || value == null) {
                // empty component
                return null;
            }
        }
        return lastValue;
    }

}
