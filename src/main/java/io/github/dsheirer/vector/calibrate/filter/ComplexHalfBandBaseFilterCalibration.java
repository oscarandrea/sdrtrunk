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
import io.github.dsheirer.dsp.filter.decimate.IComplexDecimationFilter;
import io.github.dsheirer.dsp.filter.halfband.complex.ScalarComplexHalfBandDecimationFilter;
import io.github.dsheirer.dsp.window.WindowType;
import io.github.dsheirer.vector.calibrate.Calibration;
import io.github.dsheirer.vector.calibrate.CalibrationException;
import io.github.dsheirer.vector.calibrate.CalibrationType;
import io.github.dsheirer.vector.calibrate.Implementation;
import jdk.incubator.vector.FloatVector;
import jdk.incubator.vector.VectorSpecies;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Base calibration plugin for complex half-band filters
 */
public abstract class ComplexHalfBandBaseFilterCalibration extends Calibration
{
    private static final Logger mLog = LoggerFactory.getLogger(ComplexHalfBandBaseFilterCalibration.class);
    private static final int ITERATIONS = 1_000_000;
    private static final int SAMPLE_BUFFER_SIZE = 2048;
    private float[] mCoefficients;

    /**
     * Constructs an instance
     */
    public ComplexHalfBandBaseFilterCalibration(CalibrationType type, int filterLength)
    {
        super(type);
        mCoefficients = FilterFactory.getHalfBand(filterLength, WindowType.BLACKMAN);
    }

    /**
     * Performs calibration to determine optimal (Scalar vs Vector) operation type.
     * @throws CalibrationException
     */
    @Override public void calibrate() throws CalibrationException
    {
        float[] samples = getFloatSamples(SAMPLE_BUFFER_SIZE);

        long bestScore = calculateScalar(mCoefficients, samples, ITERATIONS);
        mLog.info("COMPLEX HALF-BAND " + mCoefficients.length + "-TAP SCALAR:" + bestScore);
        Implementation operation = Implementation.SCALAR;

        switch(FloatVector.SPECIES_PREFERRED.length())
        {
            //Fall through for each switch case is the intended behavior
            case 16:
                long vector512 = calculateVector(FloatVector.SPECIES_512, mCoefficients, samples, ITERATIONS);
                mLog.info("COMPLEX HALF-BAND " + mCoefficients.length + "-TAP VECTOR 512:" + vector512);
                if(vector512 < bestScore)
                {
                    bestScore = vector512;
                    operation = Implementation.VECTOR_SIMD_512;
                }
            case 8:
                long vector256 = calculateVector(FloatVector.SPECIES_256, mCoefficients, samples, ITERATIONS);
                mLog.info("COMPLEX HALF-BAND " + mCoefficients.length + "-TAP VECTOR 256:" + vector256);
                if(vector256 < bestScore)
                {
                    bestScore = vector256;
                    operation = Implementation.VECTOR_SIMD_256;
                }
            case 4:
                long vector128 = calculateVector(FloatVector.SPECIES_128, mCoefficients, samples, ITERATIONS);
                mLog.info("COMPLEX HALF-BAND " + mCoefficients.length + "-TAP VECTOR 128:" + vector128);
                if(vector128 < bestScore)
                {
                    bestScore = vector128;
                    operation = Implementation.VECTOR_SIMD_128;
                }
            case 2:
                long vector64 = calculateVector(FloatVector.SPECIES_64, mCoefficients, samples, ITERATIONS);
                mLog.info("COMPLEX HALF-BAND " + mCoefficients.length + "-TAP VECTOR 64:" + vector64);
                if(vector64 < bestScore)
                {
                    operation = Implementation.VECTOR_SIMD_64;
                }
        }

        mLog.info("COMPLEX HALF-BAND " + mCoefficients.length + "-TAP - SETTING OPTIMAL OPERATION TO:" + operation);
        setImplementation(operation);
    }

    /**
     * Calculates the time duration to process the sample buffer with the filter coefficients.
     * @param coefficients of the filter
     * @param samples buffer
     * @param iterations count of how many times to (re)filter the samples buffer
     * @return time duration in milliseconds
     */
    private long calculateScalar(float[] coefficients, float[] samples, int iterations)
    {
        float accumulator = 0.0f;

        IComplexDecimationFilter filter = getScalarFilter(coefficients);

        long start = System.currentTimeMillis();

        for(int i = 0; i < iterations; i++)
        {
            float[] filtered = filter.decimateComplex(samples);
            accumulator += filtered[0];
        }

        return System.currentTimeMillis() - start;
    }

    /**
     * Calculates the time duration to process the sample buffer with the filter coefficients.
     * @param species of vector for the SIMD instruction lane width
     * @param coefficients of the filter
     * @param samples buffer
     * @param iterations count of how many times to (re)filter the samples buffer
     * @return time duration in milliseconds
     */
    private long calculateVector(VectorSpecies<Float> species, float[] coefficients, float[] samples, int iterations)
    {
        float accumulator = 0.0f;

        IComplexDecimationFilter vector = getVectorFilter(species, coefficients);

        if(vector == null)
        {
            return Long.MAX_VALUE;
        }

        long start = System.currentTimeMillis();

        for(int i = 0; i < iterations; i++)
        {
            float[] filtered = vector.decimateComplex(samples);
            accumulator += filtered[0];
        }

        return System.currentTimeMillis() - start;
    }

    /**
     * Vector filter for the specified species
     * @param species of SIMD vector
     * @param coefficients for the filter
     * @return filter or null
     */
    protected abstract IComplexDecimationFilter getVectorFilter(VectorSpecies<Float> species, float[] coefficients);

    /**
     * Real half-band filter implementation
     * @param coefficients for the filter
     * @return constructed scalar filter
     */
    protected IComplexDecimationFilter getScalarFilter(float[] coefficients)
    {
        return new ScalarComplexHalfBandDecimationFilter(coefficients);
    }
}
