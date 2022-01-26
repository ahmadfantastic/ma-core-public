/*
 * Copyright (C) 2021 Radix IoT LLC. All rights reserved.
 */

package com.infiniteautomation.mango.pointvalue.generator;

import java.time.Duration;
import java.time.Instant;
import java.util.Random;
import java.util.stream.Stream;

import com.serotonin.m2m2.db.dao.BatchPointValue;
import com.serotonin.m2m2.db.dao.BatchPointValueImpl;
import com.serotonin.m2m2.rt.dataImage.PointValueTime;
import com.serotonin.m2m2.vo.DataPointVO;

public class RandomPointValueGenerator implements PointValueGenerator {

    private final Random random = new Random();
    private final Instant startTime;
    private final Instant endTime;
    private final Duration period;
    private final double minimum;
    private final double maximum;

    public RandomPointValueGenerator(long startTime, long period) {
        this(Instant.ofEpochMilli(startTime), null, Duration.ofMillis(period));
    }

    public RandomPointValueGenerator(long startTime, long endTime, long period) {
        this(Instant.ofEpochMilli(startTime), Instant.ofEpochMilli(endTime), Duration.ofMillis(period));
    }

    public RandomPointValueGenerator(Instant startTime, Instant endTime, Duration period) {
        this(startTime, endTime, period, 0D, 100D);
    }

    public RandomPointValueGenerator(Instant startTime, Instant endTime, Duration period, double minimum, double maximum) {
        this.startTime = startTime;
        this.endTime = endTime;
        this.period = period;
        this.minimum = minimum;
        this.maximum = maximum;
    }

    @Override
    public BatchPointValueSupplier createSupplier(DataPointVO point) {
        return new RandomPointValueSupplier(point);
    }

    @Override
    public Stream<BatchPointValue> apply(DataPointVO point) {
        var stream = createSupplier(point).stream();
        if (endTime != null) {
            long endTimeMs = endTime.toEpochMilli();
            stream = stream.takeWhile(v -> v.getPointValue().getTime() < endTimeMs);
        }
        return stream;
    }

    private class RandomPointValueSupplier implements BatchPointValueSupplier {
        final DataPointVO point;
        Instant timestamp = RandomPointValueGenerator.this.startTime;

        public RandomPointValueSupplier(DataPointVO point) {
            this.point = point;
        }

        @Override
        public BatchPointValue get() {
            double value = random.nextDouble() * (maximum - minimum) + minimum;
            var pointValueTime = new PointValueTime(value, timestamp.toEpochMilli());
            this.timestamp = timestamp.plus(period);
            return new BatchPointValueImpl(point, pointValueTime);
        }

        @Override
        public Instant getTimestamp() {
            return timestamp;
        }

        @Override
        public void setTimestamp(Instant timestamp) {
            this.timestamp = timestamp;
        }
    }
}
