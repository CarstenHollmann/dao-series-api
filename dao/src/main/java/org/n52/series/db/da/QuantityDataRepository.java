/*
 * Copyright (C) 2015-2018 52°North Initiative for Geospatial Open Source
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

package org.n52.series.db.da;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.hibernate.Session;
import org.n52.io.request.IoParameters;
import org.n52.io.response.dataset.Data;
import org.n52.io.response.dataset.DatasetMetadata;
import org.n52.io.response.dataset.DatasetOutput;
import org.n52.io.response.dataset.ReferenceValueOutput;
import org.n52.io.response.dataset.quantity.QuantityValue;
import org.n52.series.db.DataAccessException;
import org.n52.series.db.DataRepositoryComponent;
import org.n52.series.db.beans.DatasetEntity;
import org.n52.series.db.beans.ProcedureEntity;
import org.n52.series.db.beans.QuantityDataEntity;
import org.n52.series.db.beans.QuantityDatasetEntity;
import org.n52.series.db.beans.ServiceEntity;
import org.n52.series.db.dao.DataDao;
import org.n52.series.db.dao.DbQuery;

@DataRepositoryComponent(value = "quantity", datasetEntityType = QuantityDatasetEntity.class)
public class QuantityDataRepository
        extends AbstractDataRepository<QuantityDatasetEntity, QuantityDataEntity, QuantityValue, BigDecimal> {

    @Override
    protected QuantityValue createEmptyValue() {
        return new QuantityValue();
    }

    @Override
    public List<ReferenceValueOutput<QuantityValue>> getReferenceValues(QuantityDatasetEntity datasetEntity,
            DbQuery query) {
        List<DatasetEntity> referenceValues = datasetEntity.getReferenceValues();
        List<ReferenceValueOutput<QuantityValue>> outputs = new ArrayList<>();
        for (DatasetEntity referenceSeriesEntity : referenceValues) {
            if (referenceSeriesEntity instanceof QuantityDatasetEntity) {
                ReferenceValueOutput<QuantityValue> refenceValueOutput = new ReferenceValueOutput<>();
                ProcedureEntity procedure = referenceSeriesEntity.getProcedure();
                refenceValueOutput.setLabel(procedure.getNameI18n(query.getLocale()));
                refenceValueOutput.setReferenceValueId(createReferenceDatasetId(query, referenceSeriesEntity));

                QuantityDataEntity lastValue = (QuantityDataEntity) referenceSeriesEntity.getLastObservation();
                refenceValueOutput.setLastValue(
                        assembleDataValue(lastValue, (QuantityDatasetEntity) referenceSeriesEntity, query));
                outputs.add(refenceValueOutput);
            }
        }
        return outputs;
    }

    @Override
    protected Data<QuantityValue> assembleExpandedData(QuantityDatasetEntity dataset, DbQuery query, Session session)
            throws DataAccessException {
        Data<QuantityValue> result = assembleData(dataset, query, session);
        DatasetMetadata<QuantityValue> metadata = result.getMetadata();

        QuantityDataEntity previousValue = getClosestValueBeforeStart(dataset, query);
        QuantityDataEntity nextValue = getClosestValueAfterEnd(dataset, query);
        metadata.setValueBeforeTimespan(createValue(previousValue, dataset, query));
        metadata.setValueAfterTimespan(createValue(nextValue, dataset, query));

        List<DatasetEntity> referenceValues = dataset.getReferenceValues();
        if ((referenceValues != null) && !referenceValues.isEmpty()) {
            metadata.setReferenceValues(assembleReferenceSeries(referenceValues, query, session));
        }
        return result;
    }

    private Map<String, Data<QuantityValue>> assembleReferenceSeries(List<DatasetEntity> referenceValues, DbQuery query,
            Session session) throws DataAccessException {
        Map<String, Data<QuantityValue>> referenceSeries = new HashMap<>();
        for (DatasetEntity referenceSeriesEntity : referenceValues) {
            if (referenceSeriesEntity.isPublished() && referenceSeriesEntity instanceof QuantityDatasetEntity) {
                Data<QuantityValue> referenceSeriesData = assembleData((QuantityDatasetEntity) referenceSeriesEntity,
                        query, session);
                if (haveToExpandReferenceData(referenceSeriesData)) {
                    referenceSeriesData = expandReferenceDataIfNecessary((QuantityDatasetEntity) referenceSeriesEntity,
                            query, session);
                }
                referenceSeries.put(createReferenceDatasetId(query, referenceSeriesEntity), referenceSeriesData);
            }
        }
        return referenceSeries;
    }

    protected String createReferenceDatasetId(DbQuery query, DatasetEntity referenceSeriesEntity) {
        DatasetOutput<?> dataset = DatasetOutput.create(query.getParameters());
        Long id = referenceSeriesEntity.getId();
        dataset.setId(id.toString());

        String referenceDatasetId = dataset.getId();
        return referenceDatasetId.toString();
    }

    private boolean haveToExpandReferenceData(Data<QuantityValue> referenceSeriesData) {
        return referenceSeriesData.getValues().size() <= 1;
    }

    private Data<QuantityValue> expandReferenceDataIfNecessary(QuantityDatasetEntity seriesEntity, DbQuery query,
            Session session) throws DataAccessException {
        Data<QuantityValue> result = new Data<>();
        DataDao<QuantityDataEntity> dao = createDataDao(session);
        List<QuantityDataEntity> observations = dao.getAllInstancesFor(seriesEntity, query);
        if (!hasValidEntriesWithinRequestedTimespan(observations)) {
            QuantityValue lastValue = getLastValue(seriesEntity, session, query);
            result.addValues(expandToInterval(lastValue.getValue(), seriesEntity, query));
        }

        if (hasSingleValidReferenceValue(observations)) {
            QuantityDataEntity entity = observations.get(0);
            result.addValues(expandToInterval(entity.getValue(), seriesEntity, query));
        }
        return result;
    }

    @Override
    protected Data<QuantityValue> assembleData(QuantityDatasetEntity seriesEntity, DbQuery query, Session session) {
        Data<QuantityValue> result = new Data<>();
        DataDao<QuantityDataEntity> dao = createDataDao(session);
        List<QuantityDataEntity> observations = dao.getAllInstancesFor(seriesEntity, query);
        for (QuantityDataEntity observation : observations) {
            if (observation != null) {
                result.addNewValue(assembleDataValue(observation, seriesEntity, query));
            }
        }
        return result;
    }

    private QuantityValue[] expandToInterval(BigDecimal value, QuantityDatasetEntity series, DbQuery query) {
        QuantityDataEntity referenceStart = new QuantityDataEntity();
        Date startDate = query.getTimespan().getStart().toDate();
        referenceStart.setSamplingTimeStart(startDate);
        referenceStart.setSamplingTimeEnd(startDate);
        referenceStart.setValue(value);

        Date endDate = query.getTimespan().getEnd().toDate();
        QuantityDataEntity referenceEnd = new QuantityDataEntity();
        referenceEnd.setSamplingTimeStart(endDate);
        referenceEnd.setSamplingTimeEnd(endDate);
        referenceEnd.setValue(value);

        return new QuantityValue[] { assembleDataValue(referenceStart, series, query),
                assembleDataValue(referenceEnd, series, query), };

    }

    @Override
    public QuantityValue assembleDataValue(QuantityDataEntity observation, QuantityDatasetEntity dataset,
            DbQuery query) {
        QuantityValue value = createValue(observation, dataset, query);
        return addMetadatasIfNeeded(observation, value, dataset, query);
    }

    private QuantityValue createValue(QuantityDataEntity observation, QuantityDatasetEntity dataset, DbQuery query) {
        ServiceEntity service = getServiceEntity(dataset);
        return !service.isNoDataValue(observation) ? createValue(format(observation, dataset), observation, query)
                : null;
    }

    QuantityValue createValue(BigDecimal observationValue, QuantityDataEntity observation, DbQuery query) {
        Date timeend = observation.getSamplingTimeEnd();
        Date timestart = observation.getSamplingTimeStart();
        long end = timeend.getTime();
        long start = timestart.getTime();
        IoParameters parameters = query.getParameters();
        return parameters.isShowTimeIntervals() ? new QuantityValue(start, end, observationValue)
                : new QuantityValue(end, observationValue);
    }

    private BigDecimal format(QuantityDataEntity observation, QuantityDatasetEntity series) {
        if (observation.getValue() == null) {
            return observation.getValue();
        }
        int scale = series.getNumberOfDecimals();
        return observation.getValue().setScale(scale, RoundingMode.HALF_UP);
    }

}
