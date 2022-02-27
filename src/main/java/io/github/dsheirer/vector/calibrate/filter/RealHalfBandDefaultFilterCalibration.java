/*
 * *****************************************************************************
 * Copyright (C) 2014-2022 Dennis Sheirer
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>
 * ****************************************************************************
 */

package io.github.dsheirer.vector.calibrate.filter;

import io.github.dsheirer.dsp.filter.FilterFactory;
import io.github.dsheirer.dsp.filter.decimate.IRealDecimationFilter;
import io.github.dsheirer.dsp.filter.halfband.RealHalfBandDecimationFilter;
import io.github.dsheirer.dsp.filter.halfband.VectorRealHalfBandDecimationFilter128Bit;
import io.github.dsheirer.dsp.filter.halfband.VectorRealHalfBandDecimationFilter256Bit;
import io.github.dsheirer.dsp.filter.halfband.VectorRealHalfBandDecimationFilter512Bit;
import io.github.dsheirer.dsp.filter.halfband.VectorRealHalfBandDecimationFilter64Bit;
import io.github.dsheirer.dsp.filter.halfband.VectorRealHalfBandDecimationFilterDefaultBit;
import io.github.dsheirer.dsp.window.WindowType;
import io.github.dsheirer.vector.calibrate.Calibration;
import io.github.dsheirer.vector.calibrate.CalibrationException;
import io.github.dsheirer.vector.calibrate.CalibrationType;
import io.github.dsheirer.vector.calibrate.Implementation;
import jdk.incubator.vector.FloatVector;
import jdk.incubator.vector.VectorSpecies;
import org.apache.commons.math3.stat.descriptive.moment.Mean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Base calibration plugin for real half-band filters
 */
public class RealHalfBandDefaultFilterCalibration extends Calibration
{
    private static final Logger mLog = LoggerFactory.getLogger(RealHalfBandDefaultFilterCalibration.class);
    private static final VectorSpecies<Float> VECTOR_SPECIES = FloatVector.SPECIES_PREFERRED;
    private static final int BUFFER_SIZE = 2048;
    private static final int BUFFER_ITERATIONS = 1_500;
    private static final int WARMUP_ITERATIONS = 50;
    private static final int TEST_ITERATIONS = 50;

    private IRealDecimationFilter mScalar;
    private IRealDecimationFilter mVectorPreferred;
    private IRealDecimationFilter mVector512;
    private IRealDecimationFilter mVector256;
    private IRealDecimationFilter mVector128;
    private IRealDecimationFilter mVector64;

    /**
     * Constructs an instance
     */
    public RealHalfBandDefaultFilterCalibration()
    {
        super(CalibrationType.FILTER_HALF_BAND_REAL_DEFAULT);
        float[] coefficients = FilterFactory.getHalfBand(63, WindowType.BLACKMAN);
        mScalar = new RealHalfBandDecimationFilter(coefficients);
        mVectorPreferred = new VectorRealHalfBandDecimationFilterDefaultBit(coefficients);
        mVector512 = new VectorRealHalfBandDecimationFilter512Bit(coefficients);
        mVector256 = new VectorRealHalfBandDecimationFilter256Bit(coefficients);
        mVector128 = new VectorRealHalfBandDecimationFilter128Bit(coefficients);
        mVector64 = new VectorRealHalfBandDecimationFilter64Bit(coefficients);
    }

    /**
     * Performs calibration to determine optimal (Scalar vs Vector) operation type.
     * @throws CalibrationException
     */
    @Override public void calibrate() throws CalibrationException
    {
        float[] samples = getFloatSamples(BUFFER_SIZE);

        Mean scalarMean = new Mean();

        for(int x = 0; x < WARMUP_ITERATIONS; x++)
        {
            long elapsed = testScalar(samples);
            scalarMean.increment(elapsed);
        }

        mLog.info("REAL HALF-BAND DEFAULT DECIMATE WARMUP - SCALAR: " + DECIMAL_FORMAT.format(scalarMean.getResult()));

        Mean vectorPreferredMean = new Mean();

        for(int x = 0; x < WARMUP_ITERATIONS; x++)
        {
            long elapsed = testVectorPreferred(samples);
            vectorPreferredMean.increment(elapsed);
        }

        mLog.info("REAL HALF-BAND DEFAULT DECIMATE WARMUP - VECTOR PREFERRED: " + DECIMAL_FORMAT.format(vectorPreferredMean.getResult()));
        
        Mean vector512Mean = new Mean();

        if(VECTOR_SPECIES.length() >= 16)
        {
            for(int x = 0; x < WARMUP_ITERATIONS; x++)
            {
                long elapsed = testVector512(samples);
                vector512Mean.increment(elapsed);
            }

            mLog.info("REAL HALF-BAND DEFAULT DECIMATE WARMUP - VECTOR 512: " + DECIMAL_FORMAT.format(vector512Mean.getResult()));
        }

        Mean vector256Mean = new Mean();

        if(VECTOR_SPECIES.length() >= 8)
        {
            for(int x = 0; x < WARMUP_ITERATIONS; x++)
            {
                long elapsed = testVector256(samples);
                vector256Mean.increment(elapsed);
            }

            mLog.info("REAL HALF-BAND DEFAULT DECIMATE WARMUP - VECTOR 256: " + DECIMAL_FORMAT.format(vector256Mean.getResult()));
        }

        Mean vector128Mean = new Mean();

        if(VECTOR_SPECIES.length() >= 4)
        {
            for(int x = 0; x < WARMUP_ITERATIONS; x++)
            {
                long elapsed = testVector128(samples);
                vector128Mean.increment(elapsed);
            }

            mLog.info("REAL HALF-BAND DEFAULT DECIMATE WARMUP - VECTOR 128: " + DECIMAL_FORMAT.format(vector128Mean.getResult()));
        }

        Mean vector64Mean = new Mean();

        if(VECTOR_SPECIES.length() >= 2)
        {
            for(int x = 0; x < WARMUP_ITERATIONS; x++)
            {
                long elapsed = testVector64(samples);
                vector64Mean.increment(elapsed);
            }

            mLog.info("REAL HALF-BAND DEFAULT DECIMATE WARMUP - VECTOR 64: " + DECIMAL_FORMAT.format(vector64Mean.getResult()));
        }

        //Test starts ...
        scalarMean.clear();

        for(int x = 0; x < TEST_ITERATIONS; x++)
        {
            long elapsed = testScalar(samples);
            scalarMean.increment(elapsed);
        }

        mLog.info("REAL HALF-BAND DEFAULT DECIMATE - SCALAR: " + DECIMAL_FORMAT.format(scalarMean.getResult()));

        double bestScore = scalarMean.getResult();
        setImplementation(Implementation.SCALAR);

        vectorPreferredMean.clear();

        for(int x = 0; x < TEST_ITERATIONS; x++)
        {
            long elapsed = testVectorPreferred(samples);
            vectorPreferredMean.increment(elapsed);
        }

        mLog.info("REAL HALF-BAND DEFAULT DECIMATE - VECTOR PREFERRED: " + DECIMAL_FORMAT.format(vectorPreferredMean.getResult()));

        if(vectorPreferredMean.getResult() < bestScore)
        {
            bestScore = vectorPreferredMean.getResult();
            setImplementation(Implementation.VECTOR_SIMD_PREFERRED);
        }

        if(VECTOR_SPECIES.length() >= 16)
        {
            vector512Mean.clear();

            for(int x = 0; x < TEST_ITERATIONS; x++)
            {
                long elapsed = testVector512(samples);
                vector512Mean.increment(elapsed);
            }

            mLog.info("REAL HALF-BAND DEFAULT DECIMATE - VECTOR 512: " + DECIMAL_FORMAT.format(vector512Mean.getResult()));

            if(vector512Mean.getResult() < bestScore)
            {
                bestScore = vector512Mean.getResult();
                setImplementation(Implementation.VECTOR_SIMD_512);
            }
        }

        if(VECTOR_SPECIES.length() >= 8)
        {
            vector256Mean.clear();

            for(int x = 0; x < TEST_ITERATIONS; x++)
            {
                long elapsed = testVector256(samples);
                vector256Mean.increment(elapsed);
            }

            mLog.info("REAL HALF-BAND DEFAULT DECIMATE - VECTOR 256: " + DECIMAL_FORMAT.format(vector256Mean.getResult()));

            if(vector256Mean.getResult() < bestScore)
            {
                bestScore = vector256Mean.getResult();
                setImplementation(Implementation.VECTOR_SIMD_256);
            }
        }

        if(VECTOR_SPECIES.length() >= 4)
        {
            vector128Mean.clear();

            for(int x = 0; x < TEST_ITERATIONS; x++)
            {
                long elapsed = testVector128(samples);
                vector128Mean.increment(elapsed);
            }

            mLog.info("REAL HALF-BAND DEFAULT DECIMATE - VECTOR 128: " + DECIMAL_FORMAT.format(vector128Mean.getResult()));

            if(vector128Mean.getResult() < bestScore)
            {
                bestScore = vector128Mean.getResult();
                setImplementation(Implementation.VECTOR_SIMD_128);
            }
        }

        if(VECTOR_SPECIES.length() >= 2)
        {
            vector64Mean.clear();

            for(int x = 0; x < TEST_ITERATIONS; x++)
            {
                long elapsed = testVector64(samples);
                vector64Mean.increment(elapsed);
            }

            mLog.info("REAL HALF-BAND DEFAULT DECIMATE - VECTOR 64: " + DECIMAL_FORMAT.format(vector64Mean.getResult()));

            if(vector64Mean.getResult() < bestScore)
            {
                setImplementation(Implementation.VECTOR_SIMD_64);
            }
        }

        mLog.info("REAL HALF-BAND DEFAULT DECIMATE - SET OPTIMAL IMPLEMENTATION TO: " + getImplementation());
    }

    private long testScalar(float[] samples)
    {
        double accumulator = 0.0;

        long start = System.currentTimeMillis();

        for(int x = 0; x < BUFFER_ITERATIONS; x++)
        {
            float[] filtered = mScalar.decimateReal(samples);
            accumulator += filtered[0];
        }

        return System.currentTimeMillis() - start + (long)(accumulator * 0);
    }

    private long testVectorPreferred(float[] samples)
    {
        double accumulator = 0.0;

        long start = System.currentTimeMillis();

        for(int x = 0; x < BUFFER_ITERATIONS; x++)
        {
            float[] filtered = mVectorPreferred.decimateReal(samples);
            accumulator += filtered[0];
        }

        return System.currentTimeMillis() - start + (long)(accumulator * 0);
    }

    private long testVector512(float[] samples)
    {
        double accumulator = 0.0;

        long start = System.currentTimeMillis();

        for(int x = 0; x < BUFFER_ITERATIONS; x++)
        {
            float[] filtered = mVector512.decimateReal(samples);
            accumulator += filtered[0];
        }

        return System.currentTimeMillis() - start + (long)(accumulator * 0);
    }

    private long testVector256(float[] samples)
    {
        double accumulator = 0.0;

        long start = System.currentTimeMillis();

        for(int x = 0; x < BUFFER_ITERATIONS; x++)
        {
            float[] filtered = mVector256.decimateReal(samples);
            accumulator += filtered[0];
        }

        return System.currentTimeMillis() - start + (long)(accumulator * 0);
    }

    private long testVector128(float[] samples)
    {
        double accumulator = 0.0;

        long start = System.currentTimeMillis();

        for(int x = 0; x < BUFFER_ITERATIONS; x++)
        {
            float[] filtered = mVector128.decimateReal(samples);
            accumulator += filtered[0];
        }

        return System.currentTimeMillis() - start + (long)(accumulator * 0);
    }

    private long testVector64(float[] samples)
    {
        double accumulator = 0.0;

        long start = System.currentTimeMillis();

        for(int x = 0; x < BUFFER_ITERATIONS; x++)
        {
            float[] filtered = mVector64.decimateReal(samples);
            accumulator += filtered[0];
        }

        return System.currentTimeMillis() - start + (long)(accumulator * 0);
    }
}
