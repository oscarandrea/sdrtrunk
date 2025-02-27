package io.github.dsheirer.dsp.filter.iir;

/**
 * Single pole IIR filter
 */
public class SinglePoleIirFilter
{
    /**
     * Decay value, in range: 0 <-> 1.0
     */
    private double mAlpha;

    /**
     * 1.0 - alpha
     */
    private double mOneMinusAlpha;

    /**
     * Current filter output value.
     */
    private double mOutput;

    /**
     * Constructs a single pole IIR filter
     * @param alpha decay value in range (0.0 - 1.0)
     */
    public SinglePoleIirFilter(double alpha)
    {
        if(alpha < 0.0 || alpha > 1.0)
        {
            throw new IllegalArgumentException("alpha decay value must be in range: 0.0 - 1.0");
        }

        mAlpha = alpha;
        mOneMinusAlpha = 1.0 - alpha;
    }

    /**
     * Processes the specified sample and returns the filter output
     * @param sample to process/filter
     * @return filtered output
     */
    public double filter(double sample)
    {
        mOutput = (mOutput * mOneMinusAlpha) + (mAlpha * sample);
        return mOutput;
    }

    /**
     * Current output value
     */
    public double getValue()
    {
        return mOutput;
    }
}
